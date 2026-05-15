from pathlib import Path
from urllib.request import urlretrieve


RELEASE_BASE = (
    "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0"
)

ASSETS = {
    "kokoro-v1.0.onnx": f"{RELEASE_BASE}/kokoro-v1.0.onnx",
    "voices-v1.0.bin": f"{RELEASE_BASE}/voices-v1.0.bin",
}


def download_if_missing(file_name: str, url: str, out_dir: Path) -> None:
    out_path = out_dir / file_name

    if out_path.exists() and out_path.stat().st_size > 0:
        print(f"OK: {file_name} already exists")
        return

    print(f"Downloading {file_name}...")
    urlretrieve(url, out_path)

    if not out_path.exists() or out_path.stat().st_size == 0:
        raise RuntimeError(f"Downloaded file is missing or empty: {out_path}")

    print(f"OK: downloaded {file_name}")


def main() -> None:
    out_dir = Path(__file__).parent
    out_dir.mkdir(parents=True, exist_ok=True)

    for file_name, url in ASSETS.items():
        download_if_missing(file_name, url, out_dir)

    print("All required Kokoro model files are ready.")


if __name__ == "__main__":
    main()
