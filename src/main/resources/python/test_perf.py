import time
import subprocess
from spotify_scraper import SpotifyClient, CacheConfig, FileCache, RateLimit

def benchmark_cold_spawns(tracks):
    print("\n--- 1. Cold Process Spawns (Current ProcessBuilder CLI) ---")
    durations = []
    for idx, track_id in enumerate(tracks, 1):
        start = time.perf_counter()
        subprocess.run(
            [".venv/bin/python", "scrapper.py", "track", track_id],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        elapsed_ms = (time.perf_counter() - start) * 1000
        durations.append(elapsed_ms)
        print(f"  Req #{idx} [{track_id}]: {elapsed_ms:.2f} ms")
    return durations

def benchmark_warm_client(tracks):
    print("\n--- 2. Warm In-Memory Client (Local HTTP Service Model) ---")
    durations = []
    
    # Instantiate client ONCE (keeps token and connection pool warm in RAM)
    with SpotifyClient(
		cache=CacheConfig(store=FileCache())        	
	) as client:
        for idx, track_id in enumerate(tracks, 1):
            start = time.perf_counter()
            client.get_tracks([track_id])
            elapsed_ms = (time.perf_counter() - start) * 1000
            durations.append(elapsed_ms)
            print(f"  Req #{idx} [{track_id}]: {elapsed_ms:.2f} ms")
            
    return durations

if __name__ == "__main__":
    # Use 3 real, valid Spotify track IDs
    sample_tracks = [
    "0Wh99eifNTNqDFRtzWhCE2",
	"7nis0pALSkz4Tai1hJilet",
	"6RODRlHqwSxfI6WmKjqPCL",
	"2O6G533UXYJmbkrhzmt7OC",
	"4sUUUvNseGlHTjy0Fuus4W",
	"3QKJELWORZZ7rUZDWeMWSA",
	"4im3cvYs2A3dIjBDlXJU3C",
    "0f9EpZYGXyP77Oh0bwRNV3",
    "4cOdK2wGLETKBW3PvgPWqT",
    "1301213P443213p2113221"
    ]

    print("🚀 Starting Single-Track Latency Benchmark...")
    
    cold_times = benchmark_cold_spawns(sample_tracks)
    warm_times = benchmark_warm_client(sample_tracks)

    avg_cold = sum(cold_times) / len(cold_times)
    avg_warm = sum(warm_times) / len(warm_times)
    speedup = ((avg_cold - avg_warm) / avg_cold) * 100

    print("\n📊 SUMMARY RESULTS:")
    print(f"• Avg Cold Spawn Latency : {avg_cold:.2f} ms per track")
    print(f"• Avg Warm Client Latency: {avg_warm:.2f} ms per track")
    print(f"• Latency Reduction      : {avg_cold - avg_warm:.2f} ms faster per track ({speedup:.1f}% improvement)")