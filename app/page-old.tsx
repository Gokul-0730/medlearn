"use client";

import React, { useState } from "react";

type Message = {
  sender: "user" | "bot";
  text: string;
};

type ChatResponse = {
  reply?: string;
};

const backendUrl = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

export default function Home() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);

  const sendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || loading) return;

    const userText = input.trim();
    setInput("");
    setMessages((prev) => [...prev, { sender: "user", text: userText }]);
    setLoading(true);

    try {
      const res = await fetch(`${backendUrl}/api/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message: userText }),
      });

      if (!res.ok) {
        throw new Error(`Backend returned ${res.status}`);
      }

      const data = (await res.json()) as ChatResponse;
      setMessages((prev) => [
        ...prev,
        { sender: "bot", text: data.reply ?? "The backend returned no reply." },
      ]);
    } catch {
      setMessages((prev) => [
        ...prev,
        { sender: "bot", text: "Error connecting to the Java backend." },
      ]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="min-h-screen bg-zinc-100 flex flex-col items-center p-6 text-zinc-800">
      <div className="w-full max-w-2xl bg-white shadow-md rounded-xl flex flex-col h-[85vh]">
        <header className="p-4 border-b font-semibold text-lg text-emerald-700">
          Medical Chatbot
        </header>

        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {messages.length === 0 && (
            <p className="text-zinc-400 text-center mt-12">
              Ask any medical question to begin.
            </p>
          )}
          {messages.map((m, idx) => (
            <div
              key={idx}
              className={m.sender === "user" ? "flex justify-end" : "flex justify-start"}
            >
              <div
                className={
                  m.sender === "user"
                    ? "max-w-[75%] p-3 rounded-xl text-sm bg-emerald-600 text-white"
                    : "max-w-[75%] p-3 rounded-xl text-sm bg-zinc-100 border text-zinc-800"
                }
              >
                {m.text}
              </div>
            </div>
          ))}
          {loading && (
            <div className="text-xs text-zinc-400 italic">Thinking...</div>
          )}
        </div>

        <form onSubmit={sendMessage} className="p-4 border-t flex gap-2">
          <input
            type="text"
            className="flex-1 border rounded-lg px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
            placeholder="Type your message..."
            value={input}
            onChange={(e) => setInput(e.target.value)}
          />
          <button
            type="submit"
            disabled={loading}
            className="bg-emerald-600 text-white px-5 py-2 rounded-lg text-sm font-medium hover:bg-emerald-700 disabled:opacity-50"
          >
            Send
          </button>
        </form>
      </div>
    </main>
  );
}