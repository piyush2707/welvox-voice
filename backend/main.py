import os
from fastapi import FastAPI, UploadFile
from groq import Groq

app = FastAPI()
client = Groq(api_key=os.environ["GROQ_API_KEY"])

@app.post("/dictate")
async def dictate(file: UploadFile):
    audio = await file.read()
    raw = client.audio.transcriptions.create(
        file=(file.filename, audio),
        model="whisper-large-v3",
    ).text
    clean = client.chat.completions.create(
        model="llama-3.3-70b-versatile",
        messages=[
            {"role": "system", "content": "Clean this dictation: remove filler words, fix grammar, keep the meaning. Return only the text."},
            {"role": "user", "content": raw},
        ],
    ).choices[0].message.content
    return {"raw": raw, "clean": clean}
