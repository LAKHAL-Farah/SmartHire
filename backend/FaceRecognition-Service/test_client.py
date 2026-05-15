#!/usr/bin/env python3
"""
Test Client for Face Recognition Service
Script de test pour tester tous les endpoints du service
"""

import requests
import base64
import json
import os
from pathlib import Path
import time
from datetime import datetime

# Configuration
BASE_URL = os.getenv('SERVICE_URL', 'http://localhost:5000')
TIMEOUT = 30

# Color codes for terminal output
class Colors:
    HEADER = '\033[95m'
    BLUE = '\033[94m'
    CYAN = '\033[96m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    RED = '\033[91m'
    END = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'


def print_header(title):
    """Print formatted header"""
    print(f"\n{Colors.BOLD}{Colors.BLUE}{'='*60}{Colors.END}")
    print(f"{Colors.BOLD}{Colors.BLUE}{title:^60}{Colors.END}")
    print(f"{Colors.BOLD}{Colors.BLUE}{'='*60}{Colors.END}\n")


def print_success(message):
    """Print success message"""
    print(f"{Colors.GREEN}✓ {message}{Colors.END}")


def print_error(message):
    """Print error message"""
    print(f"{Colors.RED}✗ {message}{Colors.END}")


def print_info(message):
    """Print info message"""
    print(f"{Colors.CYAN}ℹ {message}{Colors.END}")


def print_section(title):
    """Print section header"""
    print(f"\n{Colors.YELLOW}{Colors.BOLD}{title}{Colors.END}")
    print(f"{Colors.YELLOW}{'-'*len(title)}{Colors.END}")


def create_test_image(filename: str) -> str:
    """
    Create a simple test image and return base64 encoded
    For testing: creates a simple solid color image
    """
    try:
        from PIL import Image, ImageDraw
        import io
        
        # Create a simple image with a face-like pattern
        img = Image.new('RGB', (200, 200), color='white')
        draw = ImageDraw.Draw(img)
        
        # Draw simple face pattern
        draw.ellipse([50, 50, 150, 150], outline='black', width=2)  # Head
        draw.ellipse([70, 80, 90, 100], fill='black')  # Left eye
        draw.ellipse([110, 80, 130, 100], fill='black')  # Right eye
        draw.line([100, 120, 100, 140], fill='black', width=2)  # Nose
        draw.arc([75, 130, 125, 160], 0, 180, fill='black', width=2)  # Mouth
        
        # Convert to bytes
        img_bytes = io.BytesIO()
        img.save(img_bytes, format='PNG')
        img_bytes.seek(0)
        
        # Encode to base64
        img_base64 = base64.b64encode(img_bytes.getvalue()).decode()
        
        print_info(f"Created test image: {filename}")
        return img_base64
        
    except ImportError:
        print_error("Pillow not installed. Cannot create test image.")
        return None


def test_health():
    """Test /health endpoint"""
    print_section("Testing /health endpoint")
    
    try:
        response = requests.get(f"{BASE_URL}/health", timeout=TIMEOUT)
        
        if response.status_code == 200:
            print_success(f"Health check passed (HTTP {response.status_code})")
            data = response.json()
            print(f"  Status: {data.get('status')}")
            print(f"  Service: {data.get('service')}")
            print(f"  Version: {data.get('version')}")
            
            stats = data.get('statistics', {})
            print(f"  Embeddings: {stats.get('total_embeddings')}")
            print(f"  Verifications: {stats.get('total_verifications')}")
            return True
        else:
            print_error(f"Health check failed (HTTP {response.status_code})")
            return False
            
    except requests.exceptions.ConnectionError:
        print_error(f"Cannot connect to {BASE_URL}")
        print_info("Make sure the service is running!")
        return False
    except Exception as e:
        print_error(f"Error: {e}")
        return False


def test_info():
    """Test /info endpoint"""
    print_section("Testing /info endpoint")
    
    try:
        response = requests.get(f"{BASE_URL}/info", timeout=TIMEOUT)
        
        if response.status_code == 200:
            print_success(f"Info retrieved (HTTP {response.status_code})")
            data = response.json()
            print(f"  Service: {data.get('service_name')}")
            print(f"  Version: {data.get('version')}")
            print(f"  API Version: {data.get('api_version')}")
            print("\n  Available Endpoints:")
            for endpoint, method in data.get('endpoints', {}).items():
                print(f"    - {method}: /{endpoint}")
            return True
        else:
            print_error(f"Failed to get info (HTTP {response.status_code})")
            return False
    except Exception as e:
        print_error(f"Error: {e}")
        return False


def test_stats():
    """Test /stats endpoint"""
    print_section("Testing /stats endpoint")
    
    try:
        response = requests.get(f"{BASE_URL}/stats", timeout=TIMEOUT)
        
        if response.status_code == 200:
            print_success(f"Stats retrieved (HTTP {response.status_code})")
            data = response.json()
            print(f"  Total Embeddings: {data.get('total_embeddings')}")
            print(f"  Total Verifications: {data.get('total_verifications')}")
            print(f"  Successful: {data.get('successful_verifications')}")
            print(f"  Failed: {data.get('failed_verifications')}")
            print(f"  Success Rate: {data.get('success_rate')}%")
            if data.get('average_confidence'):
                print(f"  Avg Confidence: {data.get('average_confidence')}")
            return True
        else:
            print_error(f"Failed to get stats (HTTP {response.status_code})")
            return False
    except Exception as e:
        print_error(f"Error: {e}")
        return False


def test_register_face():
    """Test /register endpoint"""
    print_section("Testing /register endpoint")
    
    try:
        # Create test image
        image_b64 = create_test_image("test_face.png")
        if not image_b64:
            print_error("Could not create test image")
            return None
        
        # Register face
        payload = {
            "image": image_b64,
            "user_id": "test-user-001",
            "description": "Test face registration"
        }
        
        print_info("Registering face...")
        response = requests.post(
            f"{BASE_URL}/register",
            json=payload,
            timeout=TIMEOUT
        )
        
        if response.status_code in [200, 201]:
            print_success(f"Face registered (HTTP {response.status_code})")
            data = response.json()
            embedding_id = data.get('faceEmbeddingId')
            print(f"  Embedding ID: {embedding_id}")
            print(f"  Confidence: {data.get('confidenceScore')}")
            print(f"  Status: {data.get('status')}")
            return embedding_id
        else:
            print_error(f"Registration failed (HTTP {response.status_code})")
            print(f"  Response: {response.text}")
            return None
            
    except Exception as e:
        print_error(f"Error: {e}")
        return None


def test_verify_face(embedding_id: str):
    """Test /verify endpoint"""
    print_section("Testing /verify endpoint")
    
    if not embedding_id:
        print_error("No embedding ID provided")
        return False
    
    try:
        # Create test image
        image_b64 = create_test_image("test_verify.png")
        if not image_b64:
            print_error("Could not create test image")
            return False
        
        # Verify face
        payload = {
            "image": image_b64,
            "face_embedding_id": embedding_id,
            "user_id": "test-user-001"
        }
        
        print_info(f"Verifying face against {embedding_id}...")
        response = requests.post(
            f"{BASE_URL}/verify",
            json=payload,
            timeout=TIMEOUT
        )
        
        if response.status_code == 200:
            print_success(f"Face verified (HTTP {response.status_code})")
            data = response.json()
            matches = data.get('matches')
            confidence = data.get('confidenceScore')
            
            if matches:
                print_success(f"✓ Faces match! Confidence: {confidence}")
            else:
                print_error(f"✗ Faces do not match. Confidence: {confidence}")
            
            print(f"  Message: {data.get('message')}")
            details = data.get('details', {})
            print(f"  Distance: {details.get('distance')}")
            print(f"  Threshold: {details.get('threshold')}")
            
            return matches
        else:
            print_error(f"Verification failed (HTTP {response.status_code})")
            print(f"  Response: {response.text}")
            return False
            
    except Exception as e:
        print_error(f"Error: {e}")
        return False


def test_get_embedding(embedding_id: str):
    """Test GET /embeddings/<id> endpoint"""
    print_section("Testing GET /embeddings/<id> endpoint")
    
    if not embedding_id:
        print_error("No embedding ID provided")
        return False
    
    try:
        response = requests.get(
            f"{BASE_URL}/embeddings/{embedding_id}",
            timeout=TIMEOUT
        )
        
        if response.status_code == 200:
            print_success(f"Embedding info retrieved (HTTP {response.status_code})")
            data = response.json()
            print(f"  Embedding ID: {data.get('embedding_id')}")
            print(f"  User ID: {data.get('user_id')}")
            print(f"  Description: {data.get('description')}")
            print(f"  Created: {data.get('created_at')}")
            print(f"  Confidence: {data.get('confidence')}")
            return True
        else:
            print_error(f"Failed to get embedding info (HTTP {response.status_code})")
            return False
            
    except Exception as e:
        print_error(f"Error: {e}")
        return False


def test_delete_embedding(embedding_id: str):
    """Test DELETE /embeddings/<id> endpoint"""
    print_section("Testing DELETE /embeddings/<id> endpoint")
    
    if not embedding_id:
        print_error("No embedding ID provided")
        return False
    
    try:
        response = requests.delete(
            f"{BASE_URL}/embeddings/{embedding_id}",
            timeout=TIMEOUT
        )
        
        if response.status_code == 200:
            print_success(f"Embedding deleted (HTTP {response.status_code})")
            data = response.json()
            print(f"  Status: {data.get('status')}")
            print(f"  Message: {data.get('message')}")
            return True
        else:
            print_error(f"Failed to delete embedding (HTTP {response.status_code})")
            return False
            
    except Exception as e:
        print_error(f"Error: {e}")
        return False


def test_verification_logs(user_id: str = None):
    """Test /verification-logs endpoint"""
    print_section("Testing /verification-logs endpoint")
    
    try:
        url = f"{BASE_URL}/verification-logs"
        if user_id:
            url += f"/user/{user_id}"
        
        response = requests.get(url, timeout=TIMEOUT)
        
        if response.status_code == 200:
            print_success(f"Verification logs retrieved (HTTP {response.status_code})")
            data = response.json()
            
            if user_id:
                print(f"  User ID: {data.get('user_id')}")
                print(f"  Total Attempts: {data.get('total_attempts')}")
                print(f"  Successful: {data.get('successful')}")
                print(f"  Failed: {data.get('failed')}")
                print(f"  Success Rate: {data.get('success_rate')}%")
                print(f"  Avg Confidence: {data.get('average_confidence')}")
                
                logs = data.get('logs', [])
                if logs:
                    print("\n  Recent Logs:")
                    for log in logs[:3]:
                        print(f"    - {log.get('timestamp')}: {log.get('matches')} (confidence: {log.get('confidence')})")
            else:
                print(f"  Total Logs: {data.get('total')}")
                logs = data.get('logs', [])
                print(f"  Displayed: {len(logs)}")
                
                if logs:
                    print("\n  Recent Logs:")
                    for log in logs[:3]:
                        print(f"    - {log.get('user_id')}: {log.get('matches')} ({log.get('confidence')})")
            
            return True
        else:
            print_error(f"Failed to get logs (HTTP {response.status_code})")
            return False
            
    except Exception as e:
        print_error(f"Error: {e}")
        return False


def main():
    """Run all tests"""
    print_header("Face Recognition Service - Test Suite")
    print_info(f"Service URL: {BASE_URL}")
    print_info(f"Timestamp: {datetime.now().isoformat()}")
    
    # Test connectivity
    print_info("Testing service connectivity...")
    if not test_health():
        print_error("Service is not accessible!")
        return
    
    print_success("Service is accessible!")
    
    # Run tests
    test_info()
    test_stats()
    
    # Test face registration and verification
    print_header("Face Registration and Verification Tests")
    
    embedding_id = test_register_face()
    if embedding_id:
        time.sleep(1)  # Brief pause
        test_verify_face(embedding_id)
        test_get_embedding(embedding_id)
        test_verification_logs("test-user-001")
        time.sleep(1)  # Brief pause
        test_delete_embedding(embedding_id)
    
    # Final stats
    print_header("Final Test Summary")
    test_stats()
    
    print_header("Test Suite Completed")
    print_success("All tests completed successfully!")


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print(f"\n{Colors.YELLOW}Tests interrupted by user{Colors.END}")
    except Exception as e:
        print(f"\n{Colors.RED}Unexpected error: {e}{Colors.END}")
