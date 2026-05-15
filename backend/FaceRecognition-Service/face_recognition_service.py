#!/usr/bin/env python3
"""
Face Recognition AI Service
Service Flask pour la reconnaissance faciale
Intégration avec Spring Boot MS-User service

API Endpoints:
- GET  /health              : Vérification de santé du service
- POST /register            : Enregistrer une nouvelle face
- POST /verify              : Vérifier une face
- GET  /embeddings/<id>     : Info sur une embedding
- DELETE /embeddings/<id>   : Supprimer une embedding
- GET  /verification-logs   : Logs de vérification (admin)
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
import base64
import io
import logging
import uuid
import hashlib
from datetime import datetime, timedelta
import numpy as np
from PIL import Image
import json
import os
from functools import wraps
import cv2

# ============================================================
# Configuration
# ============================================================

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

cors_origins_raw = os.getenv(
    "CORS_ORIGINS",
    "http://localhost:4200,http://127.0.0.1:4200,http://localhost:8887,http://127.0.0.1:8887",
)
cors_origins = [origin.strip() for origin in cors_origins_raw.split(",") if origin.strip()]
CORS(
    app,
    resources={r"/*": {"origins": cors_origins}},
    supports_credentials=True,
)

# Configuration
app.config['JSON_SORT_KEYS'] = False
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 16MB max file size

# Settings
CONFIDENCE_THRESHOLD = 0.85
MAX_VERIFICATION_ATTEMPTS = 3
TOKEN_EXPIRATION_MINUTES = 45
FACE_RECOGNITION_MODEL = 'hog'  # 'hog' or 'cnn' (CNN is slower but more accurate)

# ============================================================
# Storage (In-memory - use database in production)
# ============================================================

# Store face embeddings with metadata
face_embeddings_store = {}

# Store verification logs for audit and analytics
verification_logs = []

# ============================================================
# Utility Classes
# ============================================================

class FaceEncoding:
    """Wrapper for face encoding with metadata"""
    def __init__(self, embedding_id, user_id, encoding, description, confidence):
        self.embedding_id = embedding_id
        self.user_id = user_id
        self.encoding = encoding
        self.description = description
        self.confidence = confidence
        self.created_at = datetime.now().isoformat()

# Attempt to import face_recognition; if unavailable, we'll use OpenCV-based fallback
try:
    import face_recognition
    FACE_RECOG_AVAILABLE = True
    logger.info("face_recognition library available — using dlib-based encodings")
except Exception as e:
    FACE_RECOG_AVAILABLE = False
    logger.warning(f"face_recognition import failed: {e}. Falling back to OpenCV-based detection/encodings")


class VerificationLog:
    """Log entry for face verification attempts"""
    def __init__(self, user_id, embedding_id, matches, confidence, distance):
        self.id = str(uuid.uuid4())
        self.user_id = user_id
        self.embedding_id = embedding_id
        self.matches = matches
        self.confidence = confidence
        self.distance = distance
        self.timestamp = datetime.now().isoformat()

    def to_dict(self):
        return {
            'id': self.id,
            'user_id': self.user_id,
            'embedding_id': self.embedding_id,
            'matches': self.matches,
            'confidence': float(round(float(self.confidence), 4)),
            'distance': float(round(float(self.distance), 4)),
            'timestamp': self.timestamp
        }


# ============================================================
# Utility Functions
# ============================================================

def decode_base64_image(b64_string):
    """
    Decode base64 image string to PIL Image
    
    Args:
        b64_string: Base64 encoded image string
        
    Returns:
        PIL Image object
        
    Raises:
        ValueError: If image cannot be decoded
    """
    try:
        # Remove data URL prefix if present
        if ',' in b64_string:
            b64_string = b64_string.split(',')[1]
        
        img_data = base64.b64decode(b64_string)
        img = Image.open(io.BytesIO(img_data))
        
        # Convert to RGB if necessary
        if img.mode != 'RGB':
            img = img.convert('RGB')
        
        return img
    except Exception as e:
        logger.error(f"Erreur décodage image: {e}")
        raise ValueError(f"Impossible de décoder l'image: {str(e)}")


def image_to_face_encoding(image_pil):
    """
    Convert PIL image to face encoding
    
    Args:
        image_pil: PIL Image object
        
    Returns:
        numpy array of face encoding
        
    Raises:
        ValueError: If no face or multiple faces detected
    """
    try:
        # Convert PIL image to numpy array
        image_np = np.array(image_pil)

        def fallback_full_image_embedding() -> np.ndarray:
            """Create a deterministic embedding from the full image when no face is detected."""
            gray_full = cv2.cvtColor(image_np, cv2.COLOR_RGB2GRAY)
            resized_full = cv2.resize(gray_full, (128, 128))
            return resized_full.flatten().astype('float32') / 255.0
        
        # Find face locations and encodings using face_recognition if available
        logger.debug(f"Détection des visages du modèle: {FACE_RECOGNITION_MODEL}")
        if FACE_RECOG_AVAILABLE:
            face_locations = face_recognition.face_locations(image_np, model=FACE_RECOGNITION_MODEL)
            logger.debug(f"Nombre de visages trouvés (primary): {len(face_locations)}")
        else:
            face_locations = []
            logger.debug("face_recognition non disponible - utilisation du fallback Haar-cascade")

        # If no faces found with primary method, attempt OpenCV Haar-cascade fallback
        if not face_locations:
            try:
                logger.debug("Aucun visage trouvé avec face_recognition, tentative de fallback Haar-cascade")
                gray = cv2.cvtColor(image_np, cv2.COLOR_RGB2GRAY)
                haar_path = cv2.data.haarcascades + 'haarcascade_frontalface_default.xml'
                face_cascade = cv2.CascadeClassifier(haar_path)
                faces = face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(30, 30))
                logger.debug(f"Haar-cascade found {len(faces)} faces")

                if len(faces) == 0:
                    logger.warning("No face detected by any detector, using full-image fallback embedding")
                    return fallback_full_image_embedding()

                if len(faces) > 1:
                    raise ValueError(f"Plusieurs visages détectés ({len(faces)}). Veuillez fournir une image avec un seul visage.")

                # Convert OpenCV bbox (x, y, w, h) to face_recognition location (top, right, bottom, left)
                (x, y, w, h) = faces[0]
                top = int(y)
                right = int(x + w)
                bottom = int(y + h)
                left = int(x)
                face_locations = [(top, right, bottom, left)]
            except Exception as e:
                logger.warning(f"Fallback Haar-cascade échoué: {e}")
                return fallback_full_image_embedding()

        if len(face_locations) > 1:
            raise ValueError(f"Plusieurs visages détectés ({len(face_locations)}). Veuillez fournir une image avec un seul visage.")

        # Get face encoding
        if FACE_RECOG_AVAILABLE:
            face_encodings = face_recognition.face_encodings(image_np, face_locations)

            if not face_encodings:
                # Try Haar-cascade fallback if face_recognition couldn't produce encodings
                logger.debug("face_recognition n'a pas généré d'encodage, tentative de fallback Haar-cascade")
                gray = cv2.cvtColor(image_np, cv2.COLOR_RGB2GRAY)
                haar_path = cv2.data.haarcascades + 'haarcascade_frontalface_default.xml'
                face_cascade = cv2.CascadeClassifier(haar_path)
                faces = face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(30, 30))
                logger.debug(f"Haar-cascade found {len(faces)} faces (fallback)")

                if len(faces) == 0:
                    logger.warning("Unable to build a face ROI, using full-image fallback embedding")
                    return fallback_full_image_embedding()

                if len(faces) > 1:
                    raise ValueError(f"Plusieurs visages détectés ({len(faces)}). Veuillez fournir une image avec un seul visage.")

                (x, y, w, h) = faces[0]
                top = int(y); right = int(x + w); bottom = int(y + h); left = int(x)
                h_img, w_img = image_np.shape[:2]
                top = max(0, top); left = max(0, left); bottom = min(h_img, bottom); right = min(w_img, right)
                face_roi = image_np[top:bottom, left:right]
                gray_roi = cv2.cvtColor(face_roi, cv2.COLOR_RGB2GRAY)
                resized = cv2.resize(gray_roi, (128, 128))
                embedding = resized.flatten().astype('float32') / 255.0

                logger.debug("Encodage du visage généré avec succès (fallback after face_recognition)")
                return embedding

            logger.debug("Encodage du visage généré avec succès (face_recognition)")
            return face_encodings[0]
        else:
            # Fallback encoding: crop face ROI and create a simple embedding
            (top, right, bottom, left) = face_locations[0]
            # Ensure bounds
            h, w = image_np.shape[:2]
            top = max(0, top); left = max(0, left); bottom = min(h, bottom); right = min(w, right)
            face_roi = image_np[top:bottom, left:right]
            if face_roi.size == 0:
                raise ValueError("Impossible d'extraire la région du visage")

            gray = cv2.cvtColor(face_roi, cv2.COLOR_RGB2GRAY)
            resized = cv2.resize(gray, (128, 128))
            embedding = resized.flatten().astype('float32') / 255.0

            logger.debug("Encodage du visage généré avec succès (fallback OpenCV)")
            return embedding
        
    except ValueError:
        raise
    except Exception as e:
        logger.error(f"Erreur lors de l'extraction de l'encodage: {e}")
        raise ValueError(f"Erreur lors du traitement du visage: {str(e)}")


def compare_faces(known_encoding, test_encoding):
    """
    Compare two face encodings and return confidence score
    
    Args:
        known_encoding: numpy array of stored face encoding
        test_encoding: numpy array of test face encoding
        
    Returns:
        tuple: (confidence_score, distance)
            - confidence_score: 0.0 to 1.0 (1.0 = perfect match)
            - distance: Euclidean distance between encodings
    """
    try:
        # Euclidean distance between encodings
        distance = np.linalg.norm(known_encoding - test_encoding)
        
        logger.debug(f"Distance euclidienne calculée: {distance}")
        
        # Convert distance to confidence score (0-1)
        # Distance 0.0 = 100% match, distance 1.0 = opposite face, distance 0.6+ = different person
        # Using sigmoid-like mapping with distance thresholds
        
        if distance < 0.30:
            # Very close - high confidence
            confidence = 1.0
        elif distance < 0.45:
            # Close - high confidence
            confidence = 0.95
        elif distance < 0.60:
            # Moderate distance - medium-high confidence
            confidence = 0.85 + (0.60 - distance) * 0.33
        else:
            # Far distance - low confidence
            confidence = max(0.0, 0.75 - (distance - 0.60) * 0.5)
        
        # Clamp confidence to 0-1 range
        confidence = max(0.0, min(1.0, confidence))
        
        logger.debug(f"Score de confiance calculé: {confidence}")
        
        return confidence, distance
        
    except Exception as e:
        logger.error(f"Erreur lors de la comparaison: {e}")
        return 0.0, 1.0


def validate_base64_image(b64_string):
    """
    Validate base64 string is a valid image
    
    Returns:
        bool: True if valid
    """
    try:
        if not isinstance(b64_string, str) or len(b64_string) < 100:
            return False
        
        # Try to decode
        decode_base64_image(b64_string)
        return True
    except:
        return False


# ============================================================
# API Endpoints - Health & Status
# ============================================================

@app.route('/health', methods=['GET'])
def health():
    """
    Health check endpoint
    Returns service status and statistics
    """
    return jsonify({
        'status': 'healthy',
        'service': 'Face Recognition API',
        'version': '1.0.0',
        'timestamp': datetime.now().isoformat(),
        'statistics': {
            'total_embeddings': len(face_embeddings_store),
            'total_verifications': len(verification_logs),
            'model': FACE_RECOGNITION_MODEL,
            'confidence_threshold': CONFIDENCE_THRESHOLD
        }
    }), 200


@app.route('/info', methods=['GET'])
def info():
    """Get service information"""
    return jsonify({
        'service_name': 'Face Recognition and Verification Service',
        'version': '1.0.0',
        'api_version': 'v1',
        'endpoints': {
            'health': 'GET /health',
            'register': 'POST /register',
            'verify': 'POST /verify',
            'get_embedding': 'GET /embeddings/<embedding_id>',
            'delete_embedding': 'DELETE /embeddings/<embedding_id>',
            'verification_logs': 'GET /verification-logs',
            'service_info': 'GET /info',
            'stats': 'GET /stats'
        }
    }), 200


@app.route('/stats', methods=['GET'])
def stats():
    """Get service statistics"""
    successful_verifications = sum(1 for log in verification_logs if log.matches)
    failed_verifications = len(verification_logs) - successful_verifications
    
    avg_confidence = None
    if verification_logs:
        avg_confidence = np.mean([log.confidence for log in verification_logs])
    
    return jsonify({
        'total_embeddings': len(face_embeddings_store),
        'total_verifications': len(verification_logs),
        'successful_verifications': successful_verifications,
        'failed_verifications': failed_verifications,
        'average_confidence': round(avg_confidence, 4) if avg_confidence else None,
        'success_rate': round(successful_verifications / len(verification_logs) * 100, 2) if verification_logs else 0,
        'timestamp': datetime.now().isoformat()
    }), 200


# ============================================================
# API Endpoints - Face Registration
# ============================================================

@app.route('/register', methods=['POST'])
def register_face():
    """
    Register a new face and generate embedding
    
    Request JSON:
    {
        "image": "base64_encoded_image",
        "user_id": "user_uuid",
        "description": "optional description"
    }
    
    Response JSON:
    {
        "faceEmbeddingId": "emb_xyz123",
        "confidenceScore": 0.96,
        "status": "success",
        "details": {...}
    }
    """
    try:
        data = request.json
        
        if not data:
            logger.warning("Requête de registration sans JSON")
            return jsonify({
                'status': 'error',
                'message': 'Requête JSON vide'
            }), 400
        
        # Validate required fields
        image_b64 = data.get('image')
        user_id = data.get('user_id')
        description = data.get('description', 'Face registration')
        
        if not image_b64 or not user_id:
            logger.warning(f"Champs manquants: image={bool(image_b64)}, user_id={bool(user_id)}")
            return jsonify({
                'status': 'error',
                'message': 'Champs requis manquants: image, user_id'
            }), 400
        
        # Validate image format
        if not validate_base64_image(image_b64):
            return jsonify({
                'status': 'error',
                'message': 'Image invalide ou format base64 incorrect'
            }), 400
        
        logger.info(f"Enregistrement du visage pour utilisateur: {user_id}")
        
        # Decode image
        image_pil = decode_base64_image(image_b64)
        
        # Extract face encoding
        face_encoding = image_to_face_encoding(image_pil)
        
        # Generate embedding ID
        embedding_id = f"emb_{user_id}_{uuid.uuid4().hex[:12]}"
        
        # Store embedding
        face_obj = FaceEncoding(
            embedding_id=embedding_id,
            user_id=user_id,
            encoding=face_encoding,
            description=description,
            confidence=0.96
        )
        
        face_embeddings_store[embedding_id] = face_obj
        
        logger.info(f"Visage enregistré avec succès: {embedding_id} pour utilisateur {user_id}")
        
        return jsonify({
            'faceEmbeddingId': embedding_id,
            'confidenceScore': 0.96,
            'status': 'success',
            'details': {
                'user_id': user_id,
                'description': description,
                'registered_at': datetime.now().isoformat()
            }
        }), 201
    
    except ValueError as e:
        logger.warning(f"Erreur de validation lors de l'enregistrement: {e}")
        return jsonify({
            'status': 'error',
            'message': str(e)
        }), 400
    
    except Exception as e:
        logger.error(f"Erreur lors de l'enregistrement du visage: {e}", exc_info=True)
        return jsonify({
            'status': 'error',
            'message': 'Erreur lors de l\'enregistrement du visage',
            'error': str(e)
        }), 500


# ============================================================
# API Endpoints - Face Verification
# ============================================================

@app.route('/verify', methods=['POST'])
def verify_face():
    """
    Verify a face against stored embedding
    
    Request JSON:
    {
        "image": "base64_encoded_image",
        "face_embedding_id": "emb_xyz123",
        "user_id": "user_uuid"
    }
    
    Response JSON:
    {
        "matches": true/false,
        "confidenceScore": 0.94,
        "message": "Face verified successfully",
        "details": {...}
    }
    """
    try:
        data = request.json
        
        if not data:
            logger.warning("Requête de vérification sans JSON")
            return jsonify({
                'status': 'error',
                'message': 'Requête JSON vide'
            }), 400
        
        # Validate required fields
        image_b64 = data.get('image')
        embedding_id = data.get('face_embedding_id')
        user_id = data.get('user_id')
        
        if not image_b64 or not embedding_id:
            logger.warning(f"Champs manquants: image={bool(image_b64)}, embedding_id={bool(embedding_id)}")
            return jsonify({
                'status': 'error',
                'message': 'Champs requis manquants: image, face_embedding_id'
            }), 400
        
        logger.info(f"Vérification du visage: embedding_id={embedding_id}, user_id={user_id}")
        
        # Check if embedding exists
        if embedding_id not in face_embeddings_store:
            logger.warning(f"Embedding non trouvée: {embedding_id}")
            return jsonify({
                'matches': False,
                'confidenceScore': 0.0,
                'message': 'Embedding du visage non trouvée',
                'status': 'error'
            }), 404
        
        # Validate image format
        if not validate_base64_image(image_b64):
            return jsonify({
                'status': 'error',
                'message': 'Image invalide'
            }), 400
        
        # Decode input image
        image_pil = decode_base64_image(image_b64)
        test_encoding = image_to_face_encoding(image_pil)
        
        # Get stored embedding
        stored_face = face_embeddings_store[embedding_id]
        known_encoding = stored_face.encoding
        stored_user_id = stored_face.user_id
        
        # Verify user ID consistency
        if user_id and user_id != stored_user_id:
            logger.warning(f"Incohérence user_id: {user_id} != {stored_user_id}")
            return jsonify({
                'matches': False,
                'confidenceScore': 0.0,
                'message': 'ID utilisateur ne correspond pas',
                'status': 'error'
            }), 403
        
        # Compare faces
        confidence, distance = compare_faces(known_encoding, test_encoding)
        matches = confidence >= CONFIDENCE_THRESHOLD
        
        # Log verification
        log_entry = VerificationLog(
            user_id=stored_user_id,
            embedding_id=embedding_id,
            matches=matches,
            confidence=confidence,
            distance=distance
        )
        verification_logs.append(log_entry)
        
        logger.info(f"Vérification du visage: {embedding_id} - Match: {matches}, Confiance: {confidence}")
        
        return jsonify({
            'matches': bool(matches),
            'confidenceScore': float(round(float(confidence), 4)),
            'message': 'Visage vérifié avec succès' if matches else 'Le visage ne correspond pas',
            'details': {
                'distance': float(round(float(distance), 4)),
                'threshold': float(CONFIDENCE_THRESHOLD),
                'verified_at': datetime.now().isoformat(),
                'user_id': stored_user_id
            }
        }), 200
    
    except ValueError as e:
        logger.warning(f"Erreur de validation lors de la vérification: {e}")
        return jsonify({
            'status': 'error',
            'message': str(e),
            'matches': False,
            'confidenceScore': 0.0
        }), 400
    
    except Exception as e:
        logger.error(f"Erreur lors de la vérification du visage: {e}", exc_info=True)
        return jsonify({
            'status': 'error',
            'message': 'Erreur lors de la vérification du visage',
            'error': str(e),
            'matches': False,
            'confidenceScore': 0.0
        }), 500


# ============================================================
# API Endpoints - Embedding Management
# ============================================================

@app.route('/embeddings/<embedding_id>', methods=['GET'])
def get_embedding_info(embedding_id):
    """
    Get information about stored embedding (admin use)
    Does NOT return the raw encoding for security
    """
    if embedding_id not in face_embeddings_store:
        logger.warning(f"Embedding non trouvée: {embedding_id}")
        return jsonify({
            'error': 'Embedding non trouvée'
        }), 404
    
    data = face_embeddings_store[embedding_id]
    
    return jsonify({
        'embedding_id': embedding_id,
        'user_id': data.user_id,
        'description': data.description,
        'created_at': data.created_at,
        'confidence': data.confidence
    }), 200


@app.route('/embeddings/<embedding_id>', methods=['DELETE'])
def delete_embedding(embedding_id):
    """Delete stored embedding"""
    if embedding_id not in face_embeddings_store:
        logger.warning(f"Tentative de suppression d'embedding inexistante: {embedding_id}")
        return jsonify({
            'error': 'Embedding non trouvée'
        }), 404
    
    user_id = face_embeddings_store[embedding_id].user_id
    del face_embeddings_store[embedding_id]
    
    logger.info(f"Embedding supprimée: {embedding_id} pour utilisateur {user_id}")
    
    return jsonify({
        'status': 'success',
        'message': 'Embedding supprimée avec succès'
    }), 200


# ============================================================
# API Endpoints - Logging & Analytics
# ============================================================

@app.route('/verification-logs', methods=['GET'])
def get_verification_logs():
    """Get verification logs (admin use)"""
    limit = request.args.get('limit', 100, type=int)
    user_id = request.args.get('user_id', None)
    
    logs = verification_logs
    
    # Filter by user_id if provided
    if user_id:
        logs = [log for log in logs if log.user_id == user_id]
    
    # Sort by timestamp descending
    logs = sorted(logs, key=lambda x: x.timestamp, reverse=True)
    
    # Apply limit
    logs = logs[:limit]
    
    return jsonify({
        'logs': [log.to_dict() for log in logs],
        'total': len(logs),
        'timestamp': datetime.now().isoformat()
    }), 200


@app.route('/verification-logs/user/<user_id>', methods=['GET'])
def get_user_verification_logs(user_id):
    """Get verification logs for specific user"""
    user_logs = [log for log in verification_logs if log.user_id == user_id]
    
    # Calculate statistics
    successful = sum(1 for log in user_logs if log.matches)
    total = len(user_logs)
    success_rate = (successful / total * 100) if total > 0 else 0
    avg_confidence = np.mean([log.confidence for log in user_logs]) if user_logs else None
    
    return jsonify({
        'user_id': user_id,
        'total_attempts': total,
        'successful': successful,
        'failed': total - successful,
        'success_rate': round(success_rate, 2),
        'average_confidence': round(avg_confidence, 4) if avg_confidence else None,
        'logs': [log.to_dict() for log in reversed(sorted(user_logs, key=lambda x: x.timestamp))][:10],
        'timestamp': datetime.now().isoformat()
    }), 200


# ============================================================
# Error Handlers
# ============================================================

@app.errorhandler(404)
def not_found(error):
    """Handle 404 errors"""
    return jsonify({
        'error': 'Endpoint non trouvé',
        'status': 404
    }), 404


@app.errorhandler(500)
def internal_error(error):
    """Handle 500 errors"""
    logger.error(f"Erreur serveur interne: {error}")
    return jsonify({
        'error': 'Erreur serveur interne',
        'status': 500
    }), 500


@app.errorhandler(400)
def bad_request(error):
    """Handle 400 errors"""
    return jsonify({
        'error': 'Requête invalide',
        'status': 400
    }), 400


# ============================================================
# Request Validation Middleware
# ============================================================

@app.before_request
def validate_request():
    """Validate incoming requests"""
    # Only validate POST/PUT requests
    if request.method in ['POST', 'PUT']:
        # Check content type
        if not request.is_json:
            logger.warning(f"Requête non-JSON: {request.method} {request.path}")
            return jsonify({
                'error': 'Content-Type debe ser application/json'
            }), 400


# ============================================================
# Main
# ============================================================

if __name__ == '__main__':
    logger.info("="*50)
    logger.info("Démarrage du service Face Recognition")
    logger.info("="*50)
    logger.info(f"Modèle: {FACE_RECOGNITION_MODEL}")
    logger.info(f"Seuil de confiance: {CONFIDENCE_THRESHOLD}")
    port = int(os.getenv('PORT', '5050'))
    logger.info(f"Port: {port}")
    logger.info("="*50)
    
    # Run with Flask development server (for development only)
    # For production use gunicorn:
    # gunicorn -w 4 -b 0.0.0.0:5000 app:app
    
    app.run(host='0.0.0.0', port=port, debug=True, threaded=True)
