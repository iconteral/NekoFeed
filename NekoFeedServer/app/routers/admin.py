from fastapi import APIRouter, Depends, Request, Form, HTTPException, UploadFile, File
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from sqlalchemy.orm import Session
from sqlalchemy import and_, or_
from app.database import get_db
from app.models import UpstreamFeed, FeedItem, User, UserLike, UserCollect, UserHistory
from app.schemas import UpstreamFeedCreate
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
    new_feed = UpstreamFeed(name=name, url=url, category=category)
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

@router.get("/items", response_class=HTMLResponse)
def admin_items(request: Request, category: str = None, item_type: str = None, db: Session = Depends(get_db)):
    query = db.query(FeedItem)
    if category:
        query = query.filter(FeedItem.category == category)
    if item_type:
        query = query.filter(FeedItem.item_type == item_type)
    
    items = query.order_by(FeedItem.published_at.desc()).limit(100).all()
    return templates.TemplateResponse(request=request, name="items.html", context={"request": request, "items": items, "category": category, "item_type": item_type})

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
        category=category,
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