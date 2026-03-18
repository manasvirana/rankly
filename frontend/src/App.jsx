import { useState, useEffect, useRef } from "react";
import "./App.css";

const API = "http://localhost:8080/api/v1";
const WS = "ws://localhost:8080/ws/leaderboard";

export default function App() {
  const [screen, setScreen] = useState("auth");
  const [authMode, setAuthMode] = useState("login");
  const [contests, setContests] = useState([]);
  const [activeContest, setActiveContest] = useState(null);
  const [leaderboard, setLeaderboard] = useState([]);
  const [score, setScore] = useState("");
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [message, setMessage] = useState({ text: "", type: "" });
  const [form, setForm] = useState({ username: "", email: "", password: "" });
  const [newContest, setNewContest] = useState({ name: "", description: "", endsAt: "" });
  const wsRef = useRef(null);

  useEffect(() => {
    const savedUser = localStorage.getItem("rankly_user");
    const savedToken = localStorage.getItem("rankly_token");
    if (savedUser && savedToken) {
      setUser(JSON.parse(savedUser));
      setToken(savedToken);
      setScreen("home");
    }
  }, []);

  useEffect(() => {
    if (screen === "home") fetchContests();
  }, [screen]);

  const showMessage = (text, type = "info") => {
    setMessage({ text, type });
    setTimeout(() => setMessage({ text: "", type: "" }), 3000);
  };

  const fetchContests = async () => {
    try {
      const res = await fetch(`${API}/contests?status=ACTIVE`);
      setContests(await res.json());
    } catch { showMessage("Failed to load contests", "error"); }
  };

  const handleAuth = async () => {
    try {
      const endpoint = authMode === "login" ? "/auth/login" : "/auth/register";
      const body = authMode === "login"
        ? { username: form.username, password: form.password }
        : { username: form.username, email: form.email, password: form.password };

      const res = await fetch(`${API}${endpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });

      const data = await res.json();
      if (data.token) {
        setUser(data);
        setToken(data.token);
        localStorage.setItem("rankly_user", JSON.stringify(data));
        localStorage.setItem("rankly_token", data.token);
        setScreen("home");
      } else {
        showMessage(data.error || "Something went wrong", "error");
      }
    } catch { showMessage("Something went wrong", "error"); }
  };

  const logout = () => {
    setUser(null);
    setToken(null);
    localStorage.removeItem("rankly_user");
    localStorage.removeItem("rankly_token");
    setScreen("auth");
  };

  const joinContest = async (contest) => {
    setActiveContest(contest);
    setScreen("contest");
    try {
      const res = await fetch(`${API}/contests/${contest.id}/leaderboard`);
      setLeaderboard(await res.json());
    } catch { showMessage("Failed to load leaderboard", "error"); }

    if (wsRef.current) wsRef.current.close();
    const ws = new WebSocket(`${WS}/${contest.id}`);
    ws.onmessage = (e) => {
      const update = JSON.parse(e.data);
      setLeaderboard(update.topEntries);
    };
    wsRef.current = ws;
  };

  const submitScore = async () => {
    if (!score) return showMessage("Enter a score", "error");
    try {
      const res = await fetch(`${API}/contests/${activeContest.id}/scores`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${token}`
        },
        body: JSON.stringify({ userId: user.userId, score: parseInt(score) }),
      });
      const data = await res.json();
      showMessage(`Rank #${data.rank} — Score ${Math.floor(data.score)}`, "success");
      setScore("");
    } catch { showMessage("Failed to submit score", "error"); }
  };

  const createContest = async () => {
    if (!newContest.name || !newContest.endsAt) return showMessage("Fill all fields", "error");
    try {
      const res = await fetch(`${API}/contests`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${token}`
        },
        body: JSON.stringify({
          name: newContest.name,
          description: newContest.description,
          startsAt: new Date().toISOString(),
          endsAt: new Date(newContest.endsAt).toISOString(),
        }),
      });
      const data = await res.json();
      if (data.id) {
        showMessage("Contest created!", "success");
        setNewContest({ name: "", description: "", endsAt: "" });
        fetchContests();
      } else {
        showMessage(data.error || "Failed to create contest", "error");
      }
    } catch { showMessage("Failed to create contest", "error"); }
  };

  const goHome = () => {
    setScreen("home");
    setLeaderboard([]);
    wsRef.current?.close();
  };

  if (screen === "auth") return (
    <div className="app">
      <nav className="navbar">
        <div className="nav-logo">Rank<span>ly</span></div>
        <div className="nav-tag">Real-time Leaderboards</div>
      </nav>
      <div className="page">
        <div className="hero">
          <h1>Compete.<br /><span>Rank.</span> Win.</h1>
          <p>Real-time leaderboards for live contests</p>
        </div>
        <div className="section">
          <div className="auth-tabs">
            <button className={authMode === "login" ? "tab active" : "tab"} onClick={() => setAuthMode("login")}>Login</button>
            <button className={authMode === "register" ? "tab active" : "tab"} onClick={() => setAuthMode("register")}>Register</button>
          </div>
          <div className="card">
            <div className="form-group">
              <input className="input" placeholder="Username" value={form.username}
                onChange={(e) => setForm({ ...form, username: e.target.value })} />
              {authMode === "register" && (
                <input className="input" placeholder="Email" value={form.email}
                  onChange={(e) => setForm({ ...form, email: e.target.value })} />
              )}
              <input className="input" placeholder="Password" type="password" value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                onKeyDown={(e) => e.key === "Enter" && handleAuth()} />
              <button className="btn-primary" onClick={handleAuth}>
                {authMode === "login" ? "Login" : "Create Account"}
              </button>
            </div>
            {message.text && <div className={`toast ${message.type}`}>{message.text}</div>}
          </div>
        </div>
      </div>
    </div>
  );

  if (screen === "home") return (
    <div className="app">
      <nav className="navbar">
        <div className="nav-logo">Rank<span>ly</span></div>
        <div className="nav-right">
          <span className="nav-username">{user?.username}</span>
          {user?.role === "ADMIN" && <span className="admin-badge">Admin</span>}
          <button className="btn-ghost" onClick={logout}>Logout</button>
        </div>
      </nav>
      <div className="page">
        {user?.role === "ADMIN" && (
          <div className="section">
            <div className="section-label">Create Contest</div>
            <div className="card">
              <div className="form-group">
                <input className="input" placeholder="Contest name" value={newContest.name}
                  onChange={(e) => setNewContest({ ...newContest, name: e.target.value })} />
                <input className="input" placeholder="Description" value={newContest.description}
                  onChange={(e) => setNewContest({ ...newContest, description: e.target.value })} />
                <input className="input" type="datetime-local" value={newContest.endsAt}
                  onChange={(e) => setNewContest({ ...newContest, endsAt: e.target.value })} />
                <button className="btn-primary" onClick={createContest}>Create Contest</button>
              </div>
              {message.text && <div className={`toast ${message.type}`}>{message.text}</div>}
            </div>
          </div>
        )}

        <div className="section">
          <div className="section-label">Active Contests</div>
          <div className="card">
            {contests.length === 0 ? (
              <p className="empty-state">No active contests right now</p>
            ) : (
              <div className="contest-list">
                {contests.map((c) => (
                  <div key={c.id} className="contest-item">
                    <div>
                      <p className="contest-item-name">{c.name}</p>
                      <p className="contest-item-desc">{c.description}</p>
                    </div>
                    <button className="btn-secondary" onClick={() => joinContest(c)}>Enter →</button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );

  if (screen === "contest") return (
    <div className="app">
      <nav className="navbar">
        <div className="nav-logo">Rank<span>ly</span></div>
        <div className="nav-right">
          <span className="nav-username">{user?.username}</span>
          <button className="btn-ghost" onClick={logout}>Logout</button>
        </div>
      </nav>
      <div className="page">
        <button className="back-btn" onClick={goHome}>← Back to contests</button>
        <div className="contest-hero">
          <h1>{activeContest.name}</h1>
          <div className="live-pill">
            <div className="live-dot" />
            Live
          </div>
        </div>

        <div className="section">
          <div className="section-label">Submit Score</div>
          <div className="card">
            <div className="input-group">
              <input className="input" placeholder="Enter your score" type="number"
                value={score} onChange={(e) => setScore(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && submitScore()} />
              <button className="btn-primary" onClick={submitScore}>Submit</button>
            </div>
            {message.text && <div className={`toast ${message.type}`}>{message.text}</div>}
          </div>
        </div>

        <div className="section">
          <div className="section-label">Leaderboard</div>
          <div className="card">
            {leaderboard.length === 0 ? (
              <p className="empty-state">No scores yet — be the first!</p>
            ) : (
              <table className="table">
                <thead>
                  <tr>
                    <th>Rank</th>
                    <th>Player</th>
                    <th>Score</th>
                  </tr>
                </thead>
                <tbody>
                  {leaderboard.map((entry) => (
                    <tr key={entry.userId} className={entry.username === user?.username ? "my-row" : ""}>
                      <td>{entry.rank === 1 ? "🥇" : entry.rank === 2 ? "🥈" : entry.rank === 3 ? "🥉" : `#${entry.rank}`}</td>
                      <td>{entry.username}</td>
                      <td>{Math.floor(entry.score)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}