import json
import hashlib
import time
from datetime import datetime
from dateutil import parser as date_parser
from bs4 import BeautifulSoup

def extract_image_from_html(html_content: str) -> str | None:
    if not html_content:
        return None
    try:
        soup = BeautifulSoup(html_content, 'html.parser')
        img = soup.find('img')
        if img and img.get('src'):
            return img.get('src')
    except Exception:
        pass
    return None

def normalize_feed_item(entry, feed_id: int, feed_name: str, feed_category: str) -> dict:
    link = entry.get('link', '')
    if not link:
        # Fallback to id or timestamp if no link
        link = getattr(entry, 'id', str(time.time()))
        
    item_id = f"item_{hashlib.md5(link.encode('utf-8')).hexdigest()}"
    
    title = entry.get('title', 'No Title')
    
    summary = entry.get('summary', '')
    if not summary:
        summary = entry.get('description', title)
        
    # Attempt to extract an image URL
    image_url = None
    if 'media_content' in entry and len(entry.media_content) > 0:
        for media in entry.media_content:
            if media.get('medium') == 'image' or 'image' in media.get('type', ''):
                image_url = media.get('url')
                break
        if not image_url:
             image_url = entry.media_content[0].get('url')
    
    if not image_url and 'media_thumbnail' in entry and len(entry.media_thumbnail) > 0:
        image_url = entry.media_thumbnail[0].get('url')
        
    if not image_url and 'enclosures' in entry:
        for enc in entry.enclosures:
            if enc.get('type', '').startswith('image/'):
                image_url = enc.get('href')
                break

    if not image_url:
        image_url = extract_image_from_html(summary)
        
    content_val = ''
    if 'content' in entry and len(entry.content) > 0:
        content_val = entry.content[0].value
        if not image_url:
             image_url = extract_image_from_html(content_val)

    # Parse published date
    published_at = datetime.utcnow()
    if 'published_parsed' in entry and entry.published_parsed:
        published_at = datetime.fromtimestamp(time.mktime(entry.published_parsed))
    elif 'published' in entry:
        try:
            parsed = date_parser.parse(entry.published, fuzzy=True)
            published_at = parsed.replace(tzinfo=None)
        except Exception:
            pass

    # Basic cleanup
    if summary:
        summary = BeautifulSoup(summary, "html.parser").get_text()[:500]
    else:
        summary = title

    raw_json = json.dumps({k: v for k, v in entry.items() if isinstance(v, (str, int, float, bool, list, dict))}, default=str)

    return {
        'id': item_id,
        'upstream_feed_id': feed_id,
        'title': title,
        'summary': summary,
        'content': content_val,
        'source_name': feed_name,
        'source_url': link,
        'category': feed_category,
        'item_type': 'article',
        'card_type': 'large_image' if image_url else 'small_image',
        'image_url': image_url,
        'published_at': published_at,
        'raw_json': raw_json
    }