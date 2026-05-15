import io
import os
import sys
from flask import Flask, Response, jsonify, request
from flask_cors import CORS
import soundfile as sf
from kokoro_onnx import Kokoro

app = Flask(__name__)
cors_origins_raw = os.getenv(
    "CORS_ORIGINS",
    "http://localhost:4200,http://127.0.0.1:4200,http://localhost:8887,http://127.0.0.1:8887",
)
cors_origins = [origin.strip() for origin in cors_origins_raw.split(",") if origin.strip()]
CORS(app, resources={r"/*": {"origins": cors_origins}})

script_dir = os.path.dirname(os.path.abspath(__file__))
model_path = os.path.join(script_dir, "kokoro-v1.0.onnx")
voices_path = os.path.join(script_dir, "voices-v1.0.bin")

print(f"[INFO] Loading Kokoro models from: {script_dir}", file=sys.stderr)
print(f"[INFO] Model path: {model_path}", file=sys.stderr)
print(f"[INFO] Voices path: {voices_path}", file=sys.stderr)

kokoro = Kokoro(model_path, voices_path)
print("[INFO] Kokoro loaded successfully", file=sys.stderr)


@app.get("/health")
def health():
    return jsonify({"status": "healthy", "service": "Kokoro TTS"}), 200


@app.post("/tts")
def tts():
    print("[DEBUG] Received TTS request", file=sys.stderr)
    data = request.get_json(silent=True) or {}
    text = str(data.get("text", "")).strip()

    if not text:
        return jsonify({"error": "text is required"}), 400

    try:
        print(f"[DEBUG] Synthesizing: {text}", file=sys.stderr)
        samples, sample_rate = kokoro.create(
            text,
            voice="af_sarah",
            speed=0.9,
            lang="en-us",
        )

        output = io.BytesIO()
        sf.write(output, samples, sample_rate, format="WAV")
        output.seek(0)
        print(f"[INFO] Generated audio: {len(output.getvalue())} bytes", file=sys.stderr)
        return Response(output.read(), mimetype="audio/wav")
    except Exception as exc:
        print(f"[ERROR] TTS failed: {exc}", file=sys.stderr)
        return jsonify({"error": str(exc)}), 500


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
