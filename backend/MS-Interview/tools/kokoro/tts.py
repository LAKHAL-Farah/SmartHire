# tts.py — called by Spring Boot via ProcessBuilder
# Usage: python tts.py "text to speak" output_path.wav

import sys
import soundfile as sf
from kokoro_onnx import Kokoro

if len(sys.argv) < 3:
    print("Usage: python tts.py <text> <output_path>", file=sys.stderr)
    sys.exit(1)

text = sys.argv[1]
output_path = sys.argv[2]

try:
    # af_sarah = professional American female voice
    # am_michael = professional American male voice
    kokoro = Kokoro("kokoro-v1.0.onnx", "voices-v1.0.bin")
    samples, sample_rate = kokoro.create(
        text,
        voice="af_sarah",   # change to am_michael for male voice
        speed=0.9,          # slightly slower = more professional
        lang="en-us"
    )
    sf.write(output_path, samples, sample_rate)
    print("OK", flush=True)

except Exception as e:
    print(f"ERROR: {e}", file=sys.stderr)
    sys.exit(1)