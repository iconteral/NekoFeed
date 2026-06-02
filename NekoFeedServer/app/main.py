from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.responses import RedirectResponse
import os

from app.database import engine, Base
from app.routers import api, admin

# Create tables
Base.metadata.create_all(bind=engine)

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

@app.get("/")
def root():
    return RedirectResponse(url="/admin")
