from pathlib import Path
from urllib.request import urlretrieve
import io
import os

from fastapi import FastAPI, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware
import mediapipe as mp
import numpy as np
from PIL import Image

app = FastAPI()

# Configure CORS from environment variable, defaulting to common local/gateway origins
cors_origins_str = os.getenv('CORS_ORIGINS', 'http://localhost:4200,http://127.0.0.1:4200,http://localhost:8887,http://127.0.0.1:8887')
cors_origins = [origin.strip() for origin in cors_origins_str.split(',') if origin.strip()]

app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Eye landmark indices (MediaPipe 468-point model)
LEFT_EYE = [362, 385, 387, 263, 373, 380]
RIGHT_EYE = [33, 160, 158, 133, 153, 144]
# Brow landmarks for furrow detection
LEFT_BROW = [70, 63, 105, 66, 107]
RIGHT_BROW = [336, 296, 334, 293, 300]

MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/face_landmarker/"
    "face_landmarker/float16/1/face_landmarker.task"
)
MODEL_PATH = Path(__file__).with_name("face_landmarker.task")


def ensure_model_file() -> Path:
    if MODEL_PATH.exists() and MODEL_PATH.stat().st_size > 0:
        return MODEL_PATH
    urlretrieve(MODEL_URL, MODEL_PATH)
    return MODEL_PATH


def create_landmarker():
    from mediapipe.tasks import python as mp_python
    from mediapipe.tasks.python import vision

    model_path = ensure_model_file()
    options = vision.FaceLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=str(model_path)),
        running_mode=vision.RunningMode.IMAGE,
        num_faces=1,
    )
    return vision.FaceLandmarker.create_from_options(options)


face_landmarker = create_landmarker()


def eye_aspect_ratio(lm, indices, w, h):
    pts = [(lm[i].x * w, lm[i].y * h) for i in indices]
    A = np.linalg.norm(np.array(pts[1]) - pts[5])
    B = np.linalg.norm(np.array(pts[2]) - pts[4])
    C = np.linalg.norm(np.array(pts[0]) - pts[3])
    if C == 0:
        return 0.0
    return (A + B) / (2.0 * C)


def brow_furrow_score(lm, w, h):
    l = np.array([(lm[i].x * w, lm[i].y * h) for i in LEFT_BROW])
    r = np.array([(lm[i].x * w, lm[i].y * h) for i in RIGHT_BROW])
    # Closer inner brow points = more furrow
    dist = np.linalg.norm(l[0] - r[0])
    return max(0, 1 - dist / (w * 0.15))


@app.post("/analyze")
async def analyze(frame: UploadFile = File(...)):
    img = Image.open(io.BytesIO(await frame.read())).convert("RGB")
    img_np = np.array(img)
    h, w = img_np.shape[:2]

    mp_img = mp.Image(image_format=mp.ImageFormat.SRGB, data=img_np)
    result = face_landmarker.detect(mp_img)

    if not result.face_landmarks:
        return {"face_detected": False, "stress_score": 0}

    lm = result.face_landmarks[0]
    ear_l = eye_aspect_ratio(lm, LEFT_EYE, w, h)
    ear_r = eye_aspect_ratio(lm, RIGHT_EYE, w, h)
    ear = (ear_l + ear_r) / 2.0

    blink_stress = 1.0 if ear < 0.20 else max(0, (0.30 - ear) / 0.10)
    furrow_stress = brow_furrow_score(lm, w, h)
    stress_score = round(min(1.0, blink_stress * 0.4 + furrow_stress * 0.6), 3)

    return {
        "face_detected": True,
        "stress_score": stress_score,
        "ear": round(ear, 3),
        "brow_furrow": round(furrow_stress, 3),
        "level": "high" if stress_score > 0.6 else "medium" if stress_score > 0.35 else "low",
    }