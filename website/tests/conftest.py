import subprocess
import sys
from pathlib import Path

import pytest

WEBSITE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))


@pytest.fixture(scope="session")
def site_dir() -> Path:
    """Build the site once (strict) and return the output folder."""
    result = subprocess.run(
        [sys.executable, "-m", "zensical", "build", "-s", "-c", "-f", "mkdocs.yml"],
        cwd=WEBSITE,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        pytest.fail(f"site build failed:\n{result.stdout}\n{result.stderr}", pytrace=False)
    return WEBSITE / "site"
