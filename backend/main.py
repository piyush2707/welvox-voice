import os
from fastapi import FastAPI, UploadFile, Form
from fastapi.middleware.cors import CORSMiddleware
from groq import Groq

app = FastAPI()
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)
client = Groq(api_key=os.environ["GROQ_API_KEY"])

MODES = {
    "english": "Rewrite this dictation as clean, natural English. Remove filler words, fix grammar, keep the meaning.",
    "hinglish": "Rewrite this dictation as natural Hinglish (Hindi written in Roman letters, mixed with English as the speaker did). Remove filler words and fix grammar.",
    "hindi": "Rewrite this dictation as clean Hindi in Devanagari script. Remove filler words and fix grammar.",
}

@app.post("/dictate")
async def dictate(file: UploadFile, mode: str = Form("english")):
    audio = await file.read()
    raw = client.audio.transcriptions.create(
        file=(file.filename or "audio.webm", audio),
        model="whisper-large-v3",
    ).text
    clean = client.chat.completions.create(
        model="llama-3.3-70b-versatile",
        messages=[
            {"role": "system", "content": MODES.get(mode, MODES["english"]) + " Return only the final text."},
            {"role": "user", "content": raw},
        ],
    ).choices[0].message.content
    return {"raw": raw, "clean": clean}
