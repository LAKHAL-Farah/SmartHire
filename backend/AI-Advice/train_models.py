"""
Train two models and save them to disk.

1. advice_model  — multi-label classifier: given (score, integrity, easy_wrong, hard_correct,
                   duration_min, situation, career_path) → predicts which advice tags apply.
2. category_model — ranking model: given (situation, career_path) → scores each category code.

Run once before starting the server:
    python train_models.py
"""

import joblib
import numpy as np
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import MultiLabelBinarizer
from sklearn.multiclass import OneVsRestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.neighbors import NearestNeighbors

# ── Advice model ──────────────────────────────────────────────────────────────

ADVICE_TAGS = [
    "integrity_flag",       # candidate left the quiz
    "rushing",              # completed too fast with low score
    "slow_uncertain",       # took too long with low score
    "easy_gaps",            # missed easy questions
    "hard_strength",        # got hard questions right despite low score
    "perfect",              # 100% accuracy
    "weak_topics",          # <50% on specific topics
    "score_excellent",      # >=90
    "score_good",           # 75-89
    "score_average",        # 50-74
    "score_poor",           # 30-49
    "score_critical",       # <30
    "student_encourage",    # student + low score
    "worker_balance",       # employed + medium score
    "senior_path",          # aiming for senior/lead
    "data_path",            # data/ml/ai career path
]

# Synthetic training data: [score, integrity, easy_wrong, hard_correct, duration_min, sit_idx, path_idx]
# sit_idx: 0=unknown,1=student,2=employed,3=freelancer
# path_idx: 0=unknown,1=backend,2=frontend,3=data,4=devops,5=senior

rng = np.random.default_rng(42)

def make_sample(score, integrity, easy_wrong, hard_correct, duration_min, sit, path):
    tags = []
    if integrity:
        tags.append("integrity_flag")
        return [score, int(integrity), easy_wrong, hard_correct, duration_min, sit, path], tags
    if score >= 90: tags.append("score_excellent")
    elif score >= 75: tags.append("score_good")
    elif score >= 50: tags.append("score_average")
    elif score >= 30: tags.append("score_poor")
    else: tags.append("score_critical")
    if duration_min < 3 and score < 70: tags.append("rushing")
    if duration_min > 40 and score < 60: tags.append("slow_uncertain")
    if easy_wrong > 0: tags.append("easy_gaps")
    if hard_correct > 0 and score < 75: tags.append("hard_strength")
    if easy_wrong == 0 and score == 100: tags.append("perfect")
    if sit == 1 and score < 50: tags.append("student_encourage")
    if sit == 2 and 50 <= score < 75: tags.append("worker_balance")
    if path == 5: tags.append("senior_path")
    if path == 3: tags.append("data_path")
    return [score, int(integrity), easy_wrong, hard_correct, duration_min, sit, path], tags

samples = []
# Generate 2000 synthetic samples
for _ in range(2000):
    score = int(rng.integers(0, 101))
    integrity = rng.random() < 0.1
    easy_wrong = int(rng.integers(0, 5))
    hard_correct = int(rng.integers(0, 4))
    duration_min = int(rng.integers(1, 60))
    sit = int(rng.integers(0, 4))
    path = int(rng.integers(0, 6))
    feat, tags = make_sample(score, integrity, easy_wrong, hard_correct, duration_min, sit, path)
    if tags:
        samples.append((feat, tags))

X = np.array([s[0] for s in samples], dtype=float)
y_raw = [s[1] for s in samples]

mlb = MultiLabelBinarizer(classes=ADVICE_TAGS)
Y = mlb.fit_transform(y_raw)

clf = OneVsRestClassifier(LogisticRegression(max_iter=500, C=1.0))
clf.fit(X, Y)

joblib.dump({"model": clf, "binarizer": mlb, "tags": ADVICE_TAGS}, "advice_model.joblib")
print(f"[advice_model] Trained on {len(X)} samples, {len(ADVICE_TAGS)} tags.")

# ── Category suggestion model ─────────────────────────────────────────────────

# Training data: (onboarding_text, category_code) pairs
CATEGORY_TRAINING = [
    # Java / backend
    ("student backend java spring enterprise jvm", "JAVA_OOP"),
    ("employed backend java spring", "JAVA_OOP"),
    ("backend developer java", "JAVA_OOP"),
    ("java developer", "JAVA_OOP"),
    ("full stack java spring boot", "JAVA_OOP"),
    
    # Python / data / AI
    ("student data science python ml ai", "PYTHON_CORE"),
    ("data analyst python", "PYTHON_CORE"),
    ("machine learning engineer python", "PYTHON_CORE"),
    ("ai researcher python", "PYTHON_CORE"),
    ("python developer backend", "PYTHON_CORE"),
    
    # SQL / database
    ("data analyst sql database", "SQL_BASICS"),
    ("backend developer sql database", "SQL_BASICS"),
    ("data engineer sql", "SQL_BASICS"),
    ("database administrator sql", "SQL_BASICS"),
    
    # JavaScript / frontend
    ("frontend developer javascript react angular vue", "JS_TS_WEB"),
    ("web developer javascript node", "JS_TS_WEB"),
    ("fullstack developer javascript", "JS_TS_WEB"),
    ("javascript developer", "JS_TS_WEB"),
    
    # React
    ("frontend react developer", "REACT_WEB"),
    ("react native mobile", "REACT_WEB"),
    ("ui developer react", "REACT_WEB"),
    
    # Algorithms
    ("competitive programmer algorithm cs", "DSA_BASICS"),
    ("software engineer algorithm problem solving", "DSA_BASICS"),
    ("student cs algorithm leetcode", "DSA_BASICS"),
    
    # DevOps
    ("devops engineer cloud docker kubernetes", "DOCKER_K8S"),
    ("sre infra ops cloud", "DEVOPS_ADVANCED"),
    ("cloud engineer aws", "AWS_CLOUD"),
    
    # Security
    ("security engineer cyber pentest infosec", "CYBERSECURITY"),
    ("cybersecurity analyst", "CYBERSECURITY"),
    ("web security developer", "WEB_SECURITY"),
    
    # Mobile
    ("android developer kotlin", "KOTLIN_ANDROID"),
    ("ios developer swift", "SWIFT_IOS"),
    ("mobile developer flutter", "FLUTTER_DART"),
    ("cross platform flutter dart", "FLUTTER_DART"),
    
    # C# / .NET
    ("dotnet developer csharp", "CSHARP_DOTNET"),
    ("asp.net developer", "CSHARP_DOTNET"),
    ("microsoft stack developer", "CSHARP_DOTNET"),
    
    # Other languages
    ("php web developer", "PHP_WEB"),
    ("laravel developer php", "PHP_WEB"),
    ("ruby rails developer", "RUBY_RAILS"),
    ("go golang developer", "GO_LANG"),
    ("rust systems programmer", "RUST_SYSTEMS"),
    
    # Frontend frameworks
    ("vue developer frontend", "VUE_JS"),
    ("angular developer", "ANGULAR_WEB"),
    ("node backend developer", "NODE_BACKEND"),
    
    # Data & ML
    ("data scientist machine learning", "MACHINE_LEARNING"),
    ("ml engineer tensorflow", "MACHINE_LEARNING"),
    ("data analyst pandas", "DATA_SCIENCE"),
    ("business intelligence analyst", "DATA_SCIENCE"),
    
    # Specialized
    ("blockchain developer web3", "BLOCKCHAIN_WEB3"),
    ("game developer unity", "GAME_DEV_UNITY"),
    ("ux designer user experience", "UX_UI_DESIGN"),
    ("ui designer interface", "UX_UI_DESIGN"),
    ("mongodb nosql developer", "MONGODB_NOSQL"),
    ("microservices architect", "MICROSERVICES"),
    ("ai llm developer", "GENAI_LLM"),
    ("linux system administrator", "LINUX_SHELL"),
    ("git workflow specialist", "GIT_WORKFLOW"),
    ("rest api developer", "REST_API"),
    ("spring boot developer", "SPRING_BOOT"),
]

texts = [t for t, _ in CATEGORY_TRAINING]
codes = [c for _, c in CATEGORY_TRAINING]

tfidf = TfidfVectorizer(ngram_range=(1, 2), min_df=1)
X_cat = tfidf.fit_transform(texts)

# Use KNN to find closest training examples for a new onboarding text
knn = NearestNeighbors(n_neighbors=min(10, len(texts)), metric="cosine")
knn.fit(X_cat)

joblib.dump({"tfidf": tfidf, "knn": knn, "codes": codes}, "category_model.joblib")
print(f"[category_model] Trained on {len(texts)} examples.")
print("Done. Run: python app.py")
