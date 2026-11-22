import os
import time
from collections import defaultdict

from fastapi import FastAPI, UploadFile, Form, Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from groq import Groq

app = FastAPI()
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

client = Groq(api_key=os.environ["GROQ_API_KEY"])

STT_MODEL = os.environ.get("STT_MODEL", "whisper-large-v3")
LLM_MODEL = os.environ.get("LLM_MODEL", "openai/gpt-oss-120b")
DAILY_LIMIT = int(os.environ.get("DAILY_LIMIT", "30"))  # requests per person per day
MAX_BYTES = 4 * 1024 * 1024  # about 2 minutes of speech

MODES = {
    "english": "Rewrite this dictation as clean, natural English. Remove filler words, fix grammar, keep the meaning.",
    "hinglish": "Rewrite this dictation as natural Hinglish (Hindi written in Roman letters, mixed with English as the speaker did). Remove filler words and fix grammar.",
    "hindi": "Rewrite this dictation as clean Hindi in Devanagari script. Remove filler words and fix grammar.",
}

usage = defaultdict(lambda: [0, ""])  # ip -> [count today, day]


def check_limit(request: Request):
    ip = (request.headers.get("x-forwarded-for") or request.client.host).split(",")[0].strip()
    today = time.strftime("%Y-%m-%d", time.gmtime())
    rec = usage[ip]
    if rec[1] != today:
        rec[0], rec[1] = 0, today
    if rec[0] >= DAILY_LIMIT:
        raise HTTPException(429, "Daily limit reached. Please come back tomorrow.")
    rec[0] += 1


@app.get("/")
def home():
    return {"status": "Welvox Voice is running"}


@app.post("/dictate")
async def dictate(
    request: Request,
    file: UploadFile,
    mode: str = Form("english"),
    vocab: str = Form(""),
):
    check_limit(request)
    audio = await file.read()
    if len(audio) < 1500:
        raise HTTPException(400, "That recording was too short. Try again.")
    if len(audio) > MAX_BYTES:
        raise HTTPException(413, "That recording is too long. Keep it under about 2 minutes.")

    words = [w.strip() for w in vocab.split(",") if w.strip()][:40]
    stt_args = {"prompt": "Names and terms: " + ", ".join(words) + "."} if words else {}

    system = MODES.get(mode, MODES["english"])
    if words:
        system += " Spell these names and terms exactly as given whenever they appear: " + ", ".join(words) + "."

    try:
        raw = client.audio.transcriptions.create(
            file=(file.filename or "audio.webm", audio),
            model=STT_MODEL,
            **stt_args,
        ).text
        clean = client.chat.completions.create(
            model=LLM_MODEL,
            messages=[
                {"role": "system", "content": system + " Return only the final text."},
                {"role": "user", "content": raw},
            ],
        ).choices[0].message.content
    except Exception as e:
        print("GROQ ERROR:", repr(e), flush=True)
        raise HTTPException(502, "The speech service had a problem. Please try again.")

    return {"raw": raw, "clean": clean}


class Feedback(BaseModel):
    rating: str
    mode: str = ""
    note: str = ""


@app.post("/feedback")
async def feedback(f: Feedback):
    if f.rating not in ("up", "down"):
        raise HTTPException(400, "Invalid rating.")
    # Shows up in the Render logs. Dictated text is never logged.
    print("FEEDBACK", f.rating, f.mode, f.note[:200], flush=True)
    return {"ok": True}
