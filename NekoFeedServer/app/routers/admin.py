from fastapi import APIRouter, Depends, Request, Form, HTTPException, UploadFile, File, BackgroundTasks
from fastapi.responses import HTMLResponse, RedirectResponse, JSONResponse
from fastapi.templating import Jinja2Templates
from sqlalchemy.orm import Session
from sqlalchemy import and_, or_
from app.database import get_db
from app.models import UpstreamFeed, FeedItem, User, UserLike, UserCollect, UserHistory
from app.schemas import UpstreamFeedCreate
from app.services.category_normalizer import normalize_category
import hashlib
import time
from datetime import datetime
import os
import shutil

router = APIRouter(prefix="/admin")

# Ensure templates directory is configured
templates = Jinja2Templates(directory="app/templates")

MEDIA_IMAGE_DIR = "data/media/images"
MEDIA_VIDEO_DIR = "data/media/videos"

CONTENT_TYPE_EXT = {
    "image/jpeg": ".jpg",
    "image/png": ".png",
    "image/gif": ".gif",
    "image/webp": ".webp",
    "video/mp4": ".mp4",
    "video/webm": ".webm",
    "video/ogg": ".ogv",
    "video/quicktime": ".mov",
}

def _save_upload_file(upload: UploadFile | None, target_dir: str, rel_prefix: str) -> str | None:
    if upload is None or not upload.filename:
        return None

    os.makedirs(target_dir, exist_ok=True)
    _, ext = os.path.splitext(upload.filename)
    ext = ext.lower() if ext else CONTENT_TYPE_EXT.get(upload.content_type or "", ".bin")

    filename_seed = f"{time.time()}_{upload.filename}"
    file_hash = hashlib.md5(filename_seed.encode()).hexdigest()
    filename = f"{file_hash}{ext}"
    target_path = os.path.join(target_dir, filename)

    with open(target_path, "wb") as out_file:
        shutil.copyfileobj(upload.file, out_file)

    return f"{rel_prefix}/{filename}"

@router.get("/", response_class=HTMLResponse)
def admin_dashboard(request: Request, db: Session = Depends(get_db)):
    feed_count = db.query(UpstreamFeed).count()
    item_count = db.query(FeedItem).count()
    custom_item_count = db.query(FeedItem).filter(FeedItem.is_custom == True).count()
    user_count = db.query(User).count()
    return templates.TemplateResponse(request=request, name="dashboard.html", context={
        "request": request,
        "feed_count": feed_count,
        "item_count": item_count,
        "custom_item_count": custom_item_count,
        "user_count": user_count
    })

@router.get("/feeds", response_class=HTMLResponse)
def admin_feeds(request: Request, db: Session = Depends(get_db)):
    feeds = db.query(UpstreamFeed).all()
    return templates.TemplateResponse(request=request, name="feeds.html", context={"request": request, "feeds": feeds})

@router.post("/feeds")
def add_feed(name: str = Form(...), url: str = Form(...), category: str = Form("tech"), db: Session = Depends(get_db)):
    new_feed = UpstreamFeed(name=name, url=url, category=normalize_category(category))
    db.add(new_feed)
    try:
        db.commit()
    except Exception:
        db.rollback()
    return RedirectResponse(url="/admin/feeds", status_code=303)

@router.post("/feeds/{feed_id}/delete")
def delete_feed(feed_id: int, db: Session = Depends(get_db)):
    feed = db.query(UpstreamFeed).filter(UpstreamFeed.id == feed_id).first()
    if feed:
        db.delete(feed)
        db.commit()
    return RedirectResponse(url="/admin/feeds", status_code=303)

@router.post("/feeds/{feed_id}/toggle")
def toggle_feed(feed_id: int, db: Session = Depends(get_db)):
    feed = db.query(UpstreamFeed).filter(UpstreamFeed.id == feed_id).first()
    if feed:
        feed.enabled = not feed.enabled
        db.commit()
    return RedirectResponse(url="/admin/feeds", status_code=303)


@router.get("/feeds/export")
def export_feeds(db: Session = Depends(get_db)):
    feeds = db.query(UpstreamFeed).all()
    data = []
    for f in feeds:
        data.append({
            "name": f.name,
            "url": f.url,
            "category": f.category,
            "enabled": f.enabled,
        })
    return JSONResponse(
        content={"version": 1, "feeds": data},
        headers={"Content-Disposition": "attachment; filename=feeds_export.json"}
    )


@router.post("/feeds/import")
def import_feeds(
    file: UploadFile = File(...),
    overwrite: bool = Form(False),
    db: Session = Depends(get_db)
):
    import json
    try:
        content = file.file.read()
        payload = json.loads(content)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid JSON file")

    feeds = payload.get("feeds") if isinstance(payload, dict) else payload
    if not isinstance(feeds, list):
        raise HTTPException(status_code=400, detail="JSON must contain a 'feeds' array")

    added = 0
    skipped = 0
    updated = 0
    errors = []

    for i, entry in enumerate(feeds):
        name = entry.get("name", "").strip()
        url = entry.get("url", "").strip()
        category = normalize_category(entry.get("category"))
        enabled = entry.get("enabled", True)

        if not name or not url:
            errors.append(f"Row {i+1}: missing name or url")
            continue

        existing = db.query(UpstreamFeed).filter(UpstreamFeed.url == url).first()
        if existing:
            if overwrite:
                existing.name = name
                existing.category = category
                existing.enabled = enabled
                updated += 1
            else:
                skipped += 1
            continue

        feed = UpstreamFeed(name=name, url=url, category=category, enabled=enabled)
        db.add(feed)
        added += 1

    db.commit()
    return RedirectResponse(
        url=f"/admin/feeds?msg=imported&added={added}&skipped={skipped}&updated={updated}&errors={len(errors)}",
        status_code=303
    )

@router.get("/items", response_class=HTMLResponse)
def admin_items(request: Request, category: str = None, item_type: str = None, page: int = 1, db: Session = Depends(get_db)):
    per_page = 20
    query = db.query(FeedItem)
    if category:
        query = query.filter(FeedItem.category == category)
    if item_type:
        query = query.filter(FeedItem.item_type == item_type)

    total = query.count()
    total_pages = max(1, (total + per_page - 1) // per_page)
    page = max(1, min(page, total_pages))
    offset = (page - 1) * per_page

    items = query.order_by(FeedItem.published_at.desc()).offset(offset).limit(per_page).all()
    return templates.TemplateResponse(request=request, name="items.html", context={
        "request": request, "items": items, "category": category, "item_type": item_type,
        "page": page, "total_pages": total_pages, "total": total, "per_page": per_page
    })

@router.get("/items/new", response_class=HTMLResponse)
def new_custom_item_form(request: Request):
    return templates.TemplateResponse(request=request, name="item_new.html", context={"request": request})

@router.post("/items/new")
def add_custom_item(
    title: str = Form(...),
    summary: str = Form(""),
    content: str = Form(""),
    category: str = Form("ad"),
    item_type: str = Form("ad"),
    card_type: str = Form("large_image"),
    image_url: str = Form(""),
    media_url: str = Form(""),
    image_file: UploadFile | None = File(None),
    media_file: UploadFile | None = File(None),
    tags: str = Form(""),
    db: Session = Depends(get_db)
):
    item_id = f"custom_{hashlib.md5(str(time.time()).encode()).hexdigest()}"

    local_image_path = _save_upload_file(image_file, MEDIA_IMAGE_DIR, "/media/images")
    local_media_path = _save_upload_file(media_file, MEDIA_VIDEO_DIR, "/media/videos")

    resolved_image_url = image_url.strip() if image_url else None
    resolved_media_url = media_url.strip() if media_url else None
    if local_image_path:
        resolved_image_url = None
    if local_media_path:
        resolved_media_url = None

    new_item = FeedItem(
        id=item_id,
        title=title,
        summary=summary,
        content=content,
        category=normalize_category(category),
        item_type=item_type,
        card_type=card_type,
        image_url=resolved_image_url,
        media_url=resolved_media_url,
        local_image_path=local_image_path,
        local_media_path=local_media_path,
        tags=tags,
        is_custom=True,
        published_at=datetime.utcnow()
    )
    db.add(new_item)
    db.commit()
    return RedirectResponse(url="/admin/items", status_code=303)

@router.post("/items/delete-no-media")
def delete_items_without_media(db: Session = Depends(get_db)):
    no_image = and_(
        or_(FeedItem.image_url.is_(None), FeedItem.image_url == ""),
        or_(FeedItem.local_image_path.is_(None), FeedItem.local_image_path == "")
    )
    no_media = and_(
        or_(FeedItem.media_url.is_(None), FeedItem.media_url == ""),
        or_(FeedItem.local_media_path.is_(None), FeedItem.local_media_path == "")
    )

    db.query(FeedItem).filter(no_image, no_media).delete(synchronize_session=False)
    db.commit()
    return RedirectResponse(url="/admin/items", status_code=303)

@router.get("/items/{item_id}", response_class=HTMLResponse)
def item_detail(request: Request, item_id: str, db: Session = Depends(get_db)):
    item = db.query(FeedItem).filter(FeedItem.id == item_id).first()
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")
    return templates.TemplateResponse(request=request, name="item_detail.html", context={"request": request, "item": item})

@router.get("/items/{item_id}/edit", response_class=HTMLResponse)
def edit_item_form(request: Request, item_id: str, db: Session = Depends(get_db)):
    item = db.query(FeedItem).filter(FeedItem.id == item_id).first()
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")
    return templates.TemplateResponse(request=request, name="item_edit.html", context={"request": request, "item": item})

@router.post("/items/{item_id}/edit")
def update_item(
    item_id: str,
    title: str = Form(...),
    summary: str = Form(""),
    content: str = Form(""),
    source_name: str = Form(""),
    source_url: str = Form(""),
    category: str = Form("tech"),
    item_type: str = Form("article"),
    card_type: str = Form("large_image"),
    image_url: str = Form(""),
    media_url: str = Form(""),
    image_file: UploadFile | None = File(None),
    media_file: UploadFile | None = File(None),
    tags: str = Form(""),
    brand: str = Form(""),
    cta_text: str = Form(""),
    price_text: str = Form(""),
    is_sponsored: bool = Form(False),
    ai_summary: str = Form(""),
    ai_tags: str = Form(""),
    ai_reason: str = Form(""),
    ai_enriched: bool = Form(False),
    db: Session = Depends(get_db)
):
    item = db.query(FeedItem).filter(FeedItem.id == item_id).first()
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")

    item.title = title
    item.summary = summary or None
    item.content = content or None
    item.source_name = source_name or None
    item.source_url = source_url or None
    item.category = normalize_category(category)
    item.item_type = item_type
    item.card_type = card_type
    item.tags = tags or None
    item.brand = brand or None
    item.cta_text = cta_text or None
    item.price_text = price_text or None
    item.is_sponsored = is_sponsored
    item.ai_summary = ai_summary or None
    item.ai_tags = ai_tags or None
    item.ai_reason = ai_reason or None
    item.ai_enriched = ai_enriched

    local_image_path = _save_upload_file(image_file, MEDIA_IMAGE_DIR, "/media/images")
    local_media_path = _save_upload_file(media_file, MEDIA_VIDEO_DIR, "/media/videos")

    if local_image_path:
        item.local_image_path = local_image_path
        item.image_url = None
    elif image_url.strip():
        item.image_url = image_url.strip()
        item.local_image_path = None

    if local_media_path:
        item.local_media_path = local_media_path
        item.media_url = None
    elif media_url.strip():
        item.media_url = media_url.strip()
        item.local_media_path = None

    db.commit()
    return RedirectResponse(url=f"/admin/items/{item_id}", status_code=303)

@router.post("/items/{item_id}/delete")
def delete_item(item_id: str, db: Session = Depends(get_db)):
    item = db.query(FeedItem).filter(FeedItem.id == item_id).first()
    if item:
        db.delete(item)
        db.commit()
    return RedirectResponse(url="/admin/items", status_code=303)


# User management routes
@router.get("/users", response_class=HTMLResponse)
def admin_users(request: Request, db: Session = Depends(get_db)):
    users = db.query(User).all()
    user_data = []
    for user in users:
        likes_count = db.query(UserLike).filter(UserLike.user_id == user.id).count()
        collects_count = db.query(UserCollect).filter(UserCollect.user_id == user.id).count()
        history_count = db.query(UserHistory).filter(UserHistory.user_id == user.id).count()
        user_data.append({
            **{c.name: getattr(user, c.name) for c in user.__table__.columns},
            "likes_count": likes_count,
            "collects_count": collects_count,
            "history_count": history_count
        })
    return templates.TemplateResponse(request=request, name="users.html", context={"request": request, "users": user_data})

@router.post("/users/{user_id}/toggle")
def toggle_user(user_id: int, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.id == user_id).first()
    if user:
        user.is_active = not user.is_active
        db.commit()
    return RedirectResponse(url="/admin/users", status_code=303)


@router.get("/users/{user_id}/profile", response_class=HTMLResponse)
def user_profile(request: Request, user_id: int, db: Session = Depends(get_db)):
    from collections import Counter
    from datetime import datetime, timedelta

    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    likes = db.query(UserLike).filter(UserLike.user_id == user.id).all()
    collects = db.query(UserCollect).filter(UserCollect.user_id == user.id).all()
    history = db.query(UserHistory).filter(UserHistory.user_id == user.id).all()

    like_item_ids = [l.item_id for l in likes]
    collect_item_ids = [c.item_id for c in collects]
    history_item_ids = [h.item_id for h in history]

    liked_items = {i.id: i for i in db.query(FeedItem).filter(FeedItem.id.in_(like_item_ids)).all()} if like_item_ids else {}
    collected_items = {i.id: i for i in db.query(FeedItem).filter(FeedItem.id.in_(collect_item_ids)).all()} if collect_item_ids else {}
    history_items = {i.id: i for i in db.query(FeedItem).filter(FeedItem.id.in_(history_item_ids)).all()} if history_item_ids else {}

    def calc_category_stats(items_dict):
        counter = Counter()
        for item in items_dict.values():
            if item.category:
                counter[item.category] += 1
        total = sum(counter.values()) or 1
        return [{"category": cat, "count": c, "percentage": round(c / total * 100, 1)} for cat, c in counter.most_common(10)]

    all_viewed_categories = Counter()
    for h in history:
        item = history_items.get(h.item_id)
        if item and item.category:
            all_viewed_categories[item.category] += 1
    total_views = sum(all_viewed_categories.values()) or 1
    top_categories = [{"category": cat, "count": c, "percentage": round(c / total_views * 100, 1)} for cat, c in all_viewed_categories.most_common(10)]

    tag_counter = Counter()
    for h in history:
        item = history_items.get(h.item_id)
        if item and item.tags:
            for tag in item.tags.split(','):
                tag = tag.strip()
                if tag:
                    tag_counter[tag] += 1
    top_tags = [{"tag": t, "count": c} for t, c in tag_counter.most_common(20)]

    type_counter = Counter()
    for h in history:
        item = history_items.get(h.item_id)
        if item and item.item_type:
            type_counter[item.item_type] += 1
    total_type = sum(type_counter.values()) or 1
    content_type_preferences = [{"item_type": t, "count": c, "percentage": round(c / total_type * 100, 1)} for t, c in type_counter.most_common()]

    liked_categories = calc_category_stats(liked_items)
    collected_categories = calc_category_stats(collected_items)

    total_reading_time = sum(h.duration or 0 for h in history)
    avg_reading_time = round(total_reading_time / len(history), 1) if history else 0

    hour_counter = Counter()
    for h in history:
        if h.viewed_at:
            hour_counter[h.viewed_at.hour] += 1
    reading_patterns = [{"hour": h, "count": hour_counter.get(h, 0)} for h in range(24)]

    like_rate = len(likes) / len(history) if history else 0
    collect_rate = len(collects) / len(history) if history else 0

    engagement_counter = Counter()
    for item in liked_items.values():
        if item.category:
            engagement_counter[item.category] += 1
    for item in collected_items.values():
        if item.category:
            engagement_counter[item.category] += 1
    most_engaged_category = engagement_counter.most_common(1)[0][0] if engagement_counter else None

    if history:
        view_dates = sorted(set(h.viewed_at.date() for h in history if h.viewed_at))
        max_streak = 1
        current_streak = 1
        for i in range(1, len(view_dates)):
            if (view_dates[i] - view_dates[i - 1]).days == 1:
                current_streak += 1
                max_streak = max(max_streak, current_streak)
            else:
                current_streak = 1
    else:
        max_streak = 0

    thirty_days_ago = datetime.utcnow() - timedelta(days=30)
    recent_history_30 = [h for h in history if h.viewed_at and h.viewed_at >= thirty_days_ago]
    recent_likes_30 = [l for l in likes if l.created_at and l.created_at >= thirty_days_ago]
    recent_collects_30 = [c for c in collects if c.created_at and c.created_at >= thirty_days_ago]

    daily_views = Counter()
    daily_likes = Counter()
    daily_collects = Counter()
    for h in recent_history_30:
        daily_views[h.viewed_at.strftime("%Y-%m-%d")] += 1
    for l in recent_likes_30:
        daily_likes[l.created_at.strftime("%Y-%m-%d")] += 1
    for c in recent_collects_30:
        daily_collects[c.created_at.strftime("%Y-%m-%d")] += 1
    all_dates = sorted(set(list(daily_views.keys()) + list(daily_likes.keys()) + list(daily_collects.keys())))
    daily_activities = [{"date": d, "views": daily_views.get(d, 0), "likes": daily_likes.get(d, 0), "collects": daily_collects.get(d, 0)} for d in all_dates]

    def item_to_dict(item):
        return {
            "title": item.title, "category": item.category, "item_type": item.item_type,
            "tags": item.tags.split(',') if item.tags else [], "duration": 0
        }

    recent_likes_list = [item_to_dict(liked_items[l.item_id]) for l in sorted(likes, key=lambda x: x.created_at, reverse=True)[:10] if l.item_id in liked_items]
    recent_collects_list = [item_to_dict(collected_items[c.item_id]) for c in sorted(collects, key=lambda x: x.created_at, reverse=True)[:10] if c.item_id in collected_items]
    recent_history_list = [{**item_to_dict(history_items[h.item_id]), "duration": h.duration} for h in sorted(history, key=lambda x: x.viewed_at, reverse=True)[:10] if h.item_id in history_items]

    return templates.TemplateResponse(request=request, name="user_profile.html", context={
        "request": request,
        "user": user,
        "interests": {
            "top_categories": top_categories,
            "top_tags": top_tags,
            "content_type_preferences": content_type_preferences,
            "liked_categories": liked_categories,
            "collected_categories": collected_categories,
        },
        "behavior": {
            "total_reading_time_seconds": total_reading_time,
            "avg_reading_time_seconds": avg_reading_time,
            "total_items_viewed": len(history),
            "total_likes": len(likes),
            "total_collects": len(collects),
        },
        "engagement": {
            "like_rate": round(like_rate, 3),
            "collect_rate": round(collect_rate, 3),
            "avg_daily_views": round(len(history) / 30, 1),
            "most_engaged_category": most_engaged_category,
            "longest_streak_days": max_streak,
        },
        "reading_patterns": reading_patterns,
        "daily_activities": daily_activities,
        "recent_likes": recent_likes_list,
        "recent_collects": recent_collects_list,
        "recent_history": recent_history_list,
    })


# ===== LLM Enrichment Routes =====

from app.database import SessionLocal
from app.services.llm_config import LlmConfig
from app.services.llm_enrichment import enrich_items, enrichment_status, test_llm_connection

@router.get("/llm", response_class=HTMLResponse)
def llm_settings_page(request: Request, db: Session = Depends(get_db)):
    """LLM configuration and enrichment operations page"""
    config = LlmConfig(db)
    
    total_items = db.query(FeedItem).count()
    unenriched = db.query(FeedItem).filter(FeedItem.ai_enriched == False).count()
    enriched = total_items - unenriched
    
    return templates.TemplateResponse(request=request, name="llm_settings.html", context={
        "request": request,
        "config": config,
        "total_items": total_items,
        "unenriched": unenriched,
        "enriched": enriched,
        "status": enrichment_status,
    })

@router.post("/llm/config")
def save_llm_config(
    base_url: str = Form(...),
    api_key: str = Form(""),
    model: str = Form("gpt-4o-mini"),
    db: Session = Depends(get_db)
):
    """Save config to database settings"""
    LlmConfig.save_config(db, base_url, api_key, model)
    return RedirectResponse(url="/admin/llm", status_code=303)

@router.post("/llm/test")
async def test_connection(
    base_url: str = Form(...),
    api_key: str = Form(""),
    model: str = Form("gpt-4o-mini")
):
    """Test LLM connection and return connection status"""
    config = LlmConfig()
    config.base_url = base_url.strip()
    config.api_key = api_key.strip()
    config.model = model.strip()
    
    if not config.is_configured:
        return {"success": False, "message": "Endpoint URL is required."}
        
    try:
        success = await test_llm_connection(config)
        if success:
            return {"success": True, "message": "Connection test succeeded!"}
        else:
            return {"success": False, "message": "Connection failed."}
    except Exception as e:
        return {"success": False, "message": f"Connection error: {str(e)}"}

@router.post("/llm/enrich")
def trigger_enrichment(
    background_tasks: BackgroundTasks,
    count: int = Form(10),
    reprocess: bool = Form(False),
    db: Session = Depends(get_db)
):
    """Manually trigger background task for LLM enrichment"""
    config = LlmConfig(db)
    if not config.is_configured:
        return RedirectResponse(url="/admin/llm?error=not_configured", status_code=303)
        
    if enrichment_status["running"]:
        return RedirectResponse(url="/admin/llm?error=already_running", status_code=303)
        
    query = db.query(FeedItem)
    if not reprocess:
        query = query.filter(FeedItem.ai_enriched == False)
    
    items = query.order_by(FeedItem.published_at.desc()).limit(count).all()
    item_ids = [item.id for item in items]
    
    if not item_ids:
        return RedirectResponse(url="/admin/llm?error=no_items", status_code=303)
        
    background_tasks.add_task(enrich_items, SessionLocal, item_ids, config)
    return RedirectResponse(url="/admin/llm", status_code=303)

@router.get("/llm/status")
def get_enrichment_status():
    """Retrieve enrichment status for polling"""
    return enrichment_status

@router.post("/llm/cancel")
def cancel_enrichment():
    """Cancel the current enrichment job"""
    global enrichment_status
    if enrichment_status["running"]:
        enrichment_status["running"] = False
    return RedirectResponse(url="/admin/llm", status_code=303)
