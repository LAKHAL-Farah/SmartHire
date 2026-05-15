"""
SmartHire AI-Advice microservice (Python / Flask)
Port: 8090

Endpoints:
  POST /advice          — generate personalised advice for a completed session
  POST /suggest-categories — suggest category codes based on onboarding profile

Start:
    python train_models.py   # once
    python app.py
"""

import os
import joblib
import numpy as np
from flask import Flask, request, jsonify

app = Flask(__name__)

# ── Load models ───────────────────────────────────────────────────────────────

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

try:
    advice_bundle = joblib.load(os.path.join(BASE_DIR, "advice_model.joblib"))
    ADVICE_MODEL = advice_bundle["model"]
    ADVICE_MLB = advice_bundle["binarizer"]
    ADVICE_TAGS = advice_bundle["tags"]
    print("[AI] advice_model loaded.")
except FileNotFoundError:
    ADVICE_MODEL = None
    print("[AI] advice_model.joblib not found — run train_models.py first.")

try:
    cat_bundle = joblib.load(os.path.join(BASE_DIR, "category_model.joblib"))
    CAT_TFIDF = cat_bundle["tfidf"]
    CAT_KNN = cat_bundle["knn"]
    CAT_CODES = cat_bundle["codes"]
    print("[AI] category_model loaded.")
except FileNotFoundError:
    CAT_TFIDF = None
    print("[AI] category_model.joblib not found — run train_models.py first.")

# ── Advice tag → human-readable message ──────────────────────────────────────

SIT_LABELS = {0: "", 1: "student", 2: "employed professional", 3: "freelancer"}
PATH_LABELS = {0: "", 1: "backend developer", 2: "frontend developer",
               3: "data/AI engineer", 4: "DevOps engineer", 5: "senior/lead engineer"}

TAG_MESSAGES = {
    "integrity_flag": (
        "⚠️ Your attempt was flagged because you left the quiz screen. "
        "Your score was recorded as 0%. Treat every assessment like a real interview — "
        "stay focused and avoid switching tabs or minimizing the window.",
        "💡 Next time, prepare a distraction-free environment before you start."
    ),
    "rushing": (
        "⏱️ You completed the assessment very quickly. "
        "Rushing through questions often leads to avoidable mistakes — read each question carefully.",
    ),
    "slow_uncertain": (
        "⏳ The assessment took a long time. "
        "If you were unsure on many questions, that's a signal to revisit the core material before your next attempt.",
    ),
    "easy_gaps": (
        "⚡ You missed some easy questions. "
        "These cover foundational concepts — make sure you understand the basics thoroughly.",
    ),
    "hard_strength": (
        "💪 You answered hard questions correctly despite a lower overall score. "
        "Your advanced knowledge is there — strengthen the fundamentals to unlock your full potential.",
    ),
    "perfect": (
        "🎯 Perfect accuracy — every answer was correct. Outstanding!",
    ),
    "score_excellent": (
        "🏆 Excellent result! A score above 90% shows strong mastery. Keep this momentum going.",
    ),
    "score_good": (
        "✅ Good performance. You have a solid foundation — focus on the gaps to reach expert level.",
    ),
    "score_average": (
        "📈 There is clear room for improvement. "
        "Review the questions you missed and study those concepts before your next assessment.",
    ),
    "score_poor": (
        "📚 The fundamentals need more work. "
        "Dedicate focused study time to the core topics of this category.",
    ),
    "score_critical": (
        "🔴 Significant gaps detected. Don't be discouraged — use this as a starting point "
        "and build from the basics step by step.",
    ),
    "student_encourage": (
        "🎓 As a student, every assessment is a learning opportunity. "
        "Dedicate 1–2 hours daily to practice and you will see rapid improvement.",
    ),
    "worker_balance": (
        "💼 Balancing work and learning is tough. "
        "Try 30-minute focused sessions on the weak topics during the week.",
    ),
    "senior_path": (
        "🚀 You're aiming for a senior/lead role. "
        "Consider system design, architecture patterns, and mentoring skills as your next focus areas.",
    ),
    "data_path": (
        "📊 Strong foundation for a data/AI path. "
        "Complement your skills with statistics, ML frameworks, and real dataset projects.",
    ),
}

NEXT_STEP = {
    "score_excellent": "🚀 Apply your skills in a real project or contribute to open-source to deepen your expertise.",
    "score_good":      "🛠️ Practice with hands-on exercises and small projects to reinforce the concepts.",
    "score_average":   "🛠️ Practice with hands-on exercises and small projects to reinforce the concepts.",
    "score_poor":      "📖 Revisit the official documentation or a structured course before attempting other assessments.",
    "score_critical":  "📖 Start with a structured course or bootcamp focused on the fundamentals of this category.",
}


def tags_to_advice(tags: list[str], score: int, cat_title: str,
                   situation: str, career_path: str) -> list[str]:
    messages = []

    # Integrity is standalone
    if "integrity_flag" in tags:
        for m in TAG_MESSAGES["integrity_flag"]:
            messages.append(m)
        return messages

    # Score headline
    for band in ["score_excellent", "score_good", "score_average", "score_poor", "score_critical"]:
        if band in tags:
            messages.append(TAG_MESSAGES[band][0])
            break

    # Behavioural
    for tag in ["rushing", "slow_uncertain", "easy_gaps", "hard_strength", "perfect"]:
        if tag in tags:
            messages.append(TAG_MESSAGES[tag][0])

    # Contextual (onboarding)
    for tag in ["student_encourage", "worker_balance", "senior_path", "data_path"]:
        if tag in tags:
            messages.append(TAG_MESSAGES[tag][0])

    # Personalised context line
    sit_label = situation.strip().lower() if situation else ""
    path_label = career_path.strip().lower() if career_path else ""
    if sit_label or path_label:
        ctx = "🎯 Based on your profile"
        if sit_label:
            ctx += f" ({sit_label})"
        if path_label:
            ctx += f" aiming for {path_label}"
        ctx += f": focus on {cat_title} to align with your goals."
        messages.append(ctx)

    # Next step
    for band in ["score_excellent", "score_good", "score_average", "score_poor", "score_critical"]:
        if band in tags and band in NEXT_STEP:
            messages.append(NEXT_STEP[band])
            break

    return messages


# ── Routes ────────────────────────────────────────────────────────────────────

@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "models": {
        "advice": ADVICE_MODEL is not None,
        "category": CAT_TFIDF is not None,
    }})


@app.route("/advice", methods=["POST"])
def advice():
    """
    Request body:
    {
      "score": 65,
      "integrity": false,
      "easyWrong": 2,
      "hardCorrect": 1,
      "durationMin": 18,
      "situation": "student",
      "careerPath": "backend developer",
      "categoryTitle": "Java OOP"
    }
    """
    if ADVICE_MODEL is None:
        return jsonify({"error": "Model not loaded. Run train_models.py first."}), 503

    data = request.get_json(force=True) or {}
    score = int(data.get("score", 0))
    integrity = bool(data.get("integrity", False))
    easy_wrong = int(data.get("easyWrong", 0))
    hard_correct = int(data.get("hardCorrect", 0))
    duration_min = int(data.get("durationMin", 10))
    situation = str(data.get("situation") or "")
    career_path = str(data.get("careerPath") or "")
    cat_title = str(data.get("categoryTitle") or "this assessment")

    # Encode situation / careerPath as indices (simple heuristic)
    sit_idx = _encode_situation(situation)
    path_idx = _encode_career(career_path)

    features = np.array([[score, int(integrity), easy_wrong, hard_correct,
                          duration_min, sit_idx, path_idx]], dtype=float)

    pred = ADVICE_MODEL.predict(features)
    tags = list(ADVICE_MLB.inverse_transform(pred)[0])

    messages = tags_to_advice(tags, score, cat_title, situation, career_path)
    return jsonify({"advice": messages, "tags": tags})


@app.route("/suggest-categories", methods=["POST"])
def suggest_categories():
    """
    Request body:
    {
      "situation": "student",
      "careerPath": "backend developer",
      "headline": "Full-stack developer with 3 years experience",
      "customSituation": "freelancer working remotely",
      "customCareerPath": "AI/ML engineer specializing in NLP",
      "availableCodes": ["JAVA_OOP", "PYTHON_CORE", "SQL_BASICS", "JS_FUNDAMENTALS"]
    }
    """
    if CAT_TFIDF is None:
        return jsonify({"error": "Model not loaded. Run train_models.py first."}), 503

    data = request.get_json(force=True) or {}
    situation = str(data.get("situation") or "")
    career_path = str(data.get("careerPath") or "")
    headline = str(data.get("headline") or "")
    custom_situation = str(data.get("customSituation") or "")
    custom_career_path = str(data.get("customCareerPath") or "")
    available = list(data.get("availableCodes") or [])

    # Combine all profile information for better matching
    query_parts = [situation, career_path, headline, custom_situation, custom_career_path]
    query = " ".join(part.strip() for part in query_parts if part.strip())
    
    if not query:
        return jsonify({"suggestedCodes": []})

    X_query = CAT_TFIDF.transform([query])
    distances, indices = CAT_KNN.kneighbors(X_query)

    # Collect suggested codes from nearest neighbours, deduplicated, filtered to available
    seen = set()
    suggested = []
    for idx in indices[0]:
        code = CAT_CODES[idx]
        if code not in seen and (not available or code in available):
            seen.add(code)
            suggested.append(code)

    # If we have fewer than 5 suggestions, add more from available codes based on simple keyword matching
    if len(suggested) < 5 and available:
        query_lower = query.lower()
        # Add categories based on keyword matching
        keyword_matches = []
        for code in available:
            if code not in seen:
                # Simple keyword matching for additional suggestions
                if any(keyword in query_lower for keyword in get_category_keywords(code)):
                    keyword_matches.append(code)
        
        # Add keyword matches to reach at least 5-10 suggestions
        for code in keyword_matches:
            if len(suggested) >= 10:
                break
            suggested.append(code)
            seen.add(code)
        
        # If still not enough, add remaining available codes up to 10
        for code in available:
            if len(suggested) >= 10:
                break
            if code not in seen:
                suggested.append(code)
                seen.add(code)

    # Limit to maximum 10 suggestions
    return jsonify({"suggestedCodes": suggested[:10]})


# ── Helpers ───────────────────────────────────────────────────────────────────

def get_category_keywords(code: str) -> list[str]:
    """Return keywords associated with each category for fallback matching."""
    keywords_map = {
        "JAVA_OOP": ["java", "spring", "jvm", "backend", "enterprise"],
        "PYTHON_CORE": ["python", "django", "flask", "backend", "scripting"],
        "SQL_BASICS": ["sql", "database", "mysql", "postgresql", "data"],
        "JS_TS_WEB": ["javascript", "typescript", "js", "ts", "web"],
        "REACT_WEB": ["react", "jsx", "frontend", "ui", "component"],
        "SPRING_BOOT": ["spring", "boot", "java", "microservice", "api"],
        "DOCKER_K8S": ["docker", "kubernetes", "container", "devops", "cloud"],
        "AWS_CLOUD": ["aws", "cloud", "ec2", "s3", "lambda"],
        "GIT_WORKFLOW": ["git", "github", "version", "control", "collaboration"],
        "REST_API": ["rest", "api", "http", "endpoint", "web service"],
        "WEB_SECURITY": ["security", "auth", "https", "csrf", "xss"],
        "DSA_BASICS": ["algorithm", "data structure", "leetcode", "coding"],
        "GENAI_LLM": ["ai", "llm", "gpt", "machine learning", "nlp"],
        "NODE_BACKEND": ["node", "nodejs", "express", "backend", "server"],
        "MONGODB_NOSQL": ["mongodb", "nosql", "document", "database"],
        "ANGULAR_WEB": ["angular", "typescript", "frontend", "spa"],
        "LINUX_SHELL": ["linux", "bash", "shell", "command line", "unix"],
        "MICROSERVICES": ["microservice", "distributed", "architecture"],
        "CSHARP_DOTNET": ["csharp", "c#", "dotnet", ".net", "microsoft"],
        "PHP_WEB": ["php", "laravel", "web", "backend", "server"],
        "RUBY_RAILS": ["ruby", "rails", "web", "backend"],
        "GO_LANG": ["go", "golang", "backend", "cloud", "concurrent"],
        "RUST_SYSTEMS": ["rust", "systems", "performance", "memory"],
        "KOTLIN_ANDROID": ["kotlin", "android", "mobile", "app"],
        "SWIFT_IOS": ["swift", "ios", "iphone", "mobile", "apple"],
        "FLUTTER_DART": ["flutter", "dart", "mobile", "cross platform"],
        "VUE_JS": ["vue", "vuejs", "frontend", "javascript"],
        "MACHINE_LEARNING": ["machine learning", "ml", "ai", "model", "training"],
        "DATA_SCIENCE": ["data science", "analytics", "pandas", "visualization"],
        "CYBERSECURITY": ["security", "cyber", "penetration", "infosec"],
        "DEVOPS_ADVANCED": ["devops", "ci/cd", "automation", "infrastructure"],
        "BLOCKCHAIN_WEB3": ["blockchain", "web3", "crypto", "smart contract"],
        "GAME_DEV_UNITY": ["game", "unity", "gamedev", "entertainment"],
        "UX_UI_DESIGN": ["ux", "ui", "design", "user experience", "interface"],
    }
    return keywords_map.get(code, [])


def _encode_situation(s: str) -> int:
    s = s.lower()
    if "student" in s: return 1
    if "employ" in s or "work" in s or "professional" in s: return 2
    if "freelan" in s: return 3
    return 0


def _encode_career(s: str) -> int:
    s = s.lower()
    if "senior" in s or "lead" in s or "architect" in s: return 5
    if "data" in s or "ml" in s or "ai" in s or "machine" in s: return 3
    if "devops" in s or "cloud" in s or "infra" in s: return 4
    if "frontend" in s or "react" in s or "angular" in s or "vue" in s: return 2
    if "backend" in s or "java" in s or "spring" in s: return 1
    return 0


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8090))
    print(f"[AI] Starting SmartHire AI-Advice on port {port}")
    app.run(host="0.0.0.0", port=port, debug=False)
