from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.responses import RedirectResponse
import os

from app.database import engine, Base, SessionLocal, ensure_feed_metric_columns
from app.routers import api, admin, user, user_interaction
from app.services.category_normalizer import migrate_categories

# Create tables
Base.metadata.create_all(bind=engine)
ensure_feed_metric_columns()
with SessionLocal() as db:
    migrate_categories(db)

app = FastAPI(title="Local Feed Aggregator")

# Mount static files
app.mount("/static", StaticFiles(directory="app/static"), name="static")

# Mount media directory
os.makedirs("data/media/images", exist_ok=True)
os.makedirs("data/media/videos", exist_ok=True)
app.mount("/media", StaticFiles(directory="data/media"), name="media")

# Include routers
app.include_router(api.router)
app.include_router(admin.router)
app.include_router(user.router)
app.include_router(user_interaction.router)

@app.get("/")
def root():
    return RedirectResponse(url="/admin")
