import os
import hashlib
import httpx
import logging

logger = logging.getLogger(__name__)

MEDIA_DIR = "data/media"
IMAGE_DIR = os.path.join(MEDIA_DIR, "images")
VIDEO_DIR = os.path.join(MEDIA_DIR, "videos")

async def download_media(url: str, is_video: bool = False) -> str | None:
    """Downloads a media file and returns the local relative path."""
    if not url:
        return None
    
    try:
        # Extract extension or fallback
        ext = url.split('.')[-1][:4] if '.' in url.split('/')[-1] else ('mp4' if is_video else 'jpg')
        if '?' in ext:
            ext = ext.split('?')[0]
        
        # Hash URL for a safe filename
        url_hash = hashlib.md5(url.encode('utf-8')).hexdigest()
        filename = f"{url_hash}.{ext}"
        
        target_dir = VIDEO_DIR if is_video else IMAGE_DIR
        filepath = os.path.join(target_dir, filename)
        
        # Return relative path for URL construction in API
        rel_path = f"/media/videos/{filename}" if is_video else f"/media/images/{filename}"
        
        if os.path.exists(filepath):
            return rel_path # Already downloaded
            
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(url, follow_redirects=True)
            response.raise_for_status()
            with open(filepath, 'wb') as f:
                f.write(response.content)
        return rel_path
    except Exception as e:
        logger.error(f"Failed to download media {url}: {e}")
        return None