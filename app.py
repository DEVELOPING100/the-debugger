"""SCRUM-18 spike: Java <-> Flask REST/JSON contract for question grouping.
Run: pip install flask scikit-learn  then  python app.py   (listens on port 5000)
"""
from flask import Flask, jsonify, request
from sklearn.cluster import AgglomerativeClustering
from sklearn.feature_extraction.text import TfidfVectorizer

app = Flask(__name__)
THRESHOLD = 0.95  # best setting from spike SCRUM-17


@app.post("/group")
def group():
    body = request.get_json(silent=True)
    if not body or not isinstance(body.get("requests"), list):
        return jsonify(error="Body must be {\"requests\": [{\"id\": ..., \"description\": ...}]}"), 400
    reqs = [r for r in body["requests"] if r.get("id") and r.get("description")]
    if len(reqs) < 2:  # nothing to group
        return jsonify(clusters=[{"cluster_id": "c1", "label": "", "request_ids": [r["id"] for r in reqs]}] if reqs else [])
    texts = [r["description"] for r in reqs]
    vec = TfidfVectorizer(lowercase=True, stop_words="english")
    X = vec.fit_transform(texts)
    labels = AgglomerativeClustering(n_clusters=None, metric="cosine", linkage="average",
                                     distance_threshold=THRESHOLD).fit_predict(X.toarray())
    words = vec.get_feature_names_out()
    clusters = []
    for n, lab in enumerate(sorted(set(labels)), start=1):
        idx = [i for i, l in enumerate(labels) if l == lab]
        top = X[idx].sum(axis=0).A1.argsort()[::-1][:2]  # 2 strongest words as a label
        clusters.append({"cluster_id": f"c{n}", "label": " ".join(words[j] for j in top),
                         "request_ids": [reqs[i]["id"] for i in idx]})
    return jsonify(clusters=clusters)


if __name__ == "__main__":
    app.run(port=5000)
