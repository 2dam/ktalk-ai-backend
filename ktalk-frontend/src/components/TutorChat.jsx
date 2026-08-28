import { useEffect, useState } from 'react'
import axios from 'axios'
import { TUTOR_URL, authHeaders } from '../api'

// ecue의 "AI에게 역할 부여" 패러다임을 한국어 학습용으로 차용한 역할 기반 튜터.
// 발음코치/회화파트너/문법선생님/이해입력가이드 중 하나를 골라 대화한다.
export default function TutorChat() {
  const [roles, setRoles] = useState([])
  const [role, setRole] = useState('CONVERSATION_PARTNER')
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    axios.get(`${TUTOR_URL}/roles`).then((res) => setRoles(res.data?.data || [])).catch(() => setRoles([]))
  }, [])

  const send = async (e) => {
    e.preventDefault()
    const text = input.trim()
    if (!text) return
    setMessages((m) => [...m, { role: 'user', text }])
    setInput(''); setIsSending(true); setError('')
    try {
      const res = await axios.post(`${TUTOR_URL}/chat`,
        { role, message: text, context: '' },
        { headers: { 'Content-Type': 'application/json; charset=utf-8', ...authHeaders() } })
      const data = res.data?.data
      setMessages((m) => [...m, { role: 'tutor', text: data?.reply || '(응답 없음)', label: data?.label }])
    } catch (err) {
      setError(err.response?.data?.message || '튜터가 응답하지 못했어요.')
    } finally {
      setIsSending(false)
    }
  }

  return (
    <div>
      <h3 style={{ marginTop: 0 }}>🎭 역할 튜터</h3>
      <p style={{ color: '#666', fontSize: '13px' }}>원하는 역할의 AI 튜터와 대화하며 연습해요.</p>

      <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '12px' }}>
        {roles.map((r) => (
          <button key={r.id} type="button" onClick={() => setRole(r.id)}
            style={{
              padding: '6px 12px', borderRadius: '999px', cursor: 'pointer', fontSize: '13px',
              border: '1px solid #ccc',
              backgroundColor: role === r.id ? '#8b5cf6' : '#fff', color: role === r.id ? '#fff' : '#555',
            }}>
            {r.label}
          </button>
        ))}
      </div>

      {error && <p style={{ color: '#dc3545', fontSize: '13px' }}>⚠ {error}</p>}

      <div style={{ border: '1px solid #ddd', borderRadius: '8px', padding: '14px', minHeight: '140px', marginBottom: '12px' }}>
        {messages.length === 0 && <p style={{ color: '#999', fontSize: '13px' }}>역할을 고르고 메시지를 보내보세요.</p>}
        {messages.map((m, i) => (
          <div key={i} style={{ marginBottom: '10px', textAlign: m.role === 'user' ? 'right' : 'left' }}>
            <div style={{
              display: 'inline-block', padding: '8px 12px', borderRadius: '12px', maxWidth: '80%',
              background: m.role === 'user' ? '#0ea5e9' : '#f3f0ff', color: m.role === 'user' ? '#fff' : '#333',
              fontSize: '14px',
            }}>
              {m.role === 'tutor' && m.label && <div style={{ fontSize: '11px', opacity: 0.7, marginBottom: '2px' }}>{m.label}</div>}
              {m.text}
            </div>
          </div>
        ))}
        {isSending && <p style={{ color: '#999', fontSize: '13px' }}>튜터가 생각하는 중...</p>}
      </div>

      <form onSubmit={send} style={{ display: 'flex', gap: '8px' }}>
        <input value={input} onChange={(e) => setInput(e.target.value)} placeholder="메시지 입력..."
          style={{ flex: 1, padding: '10px', fontSize: '14px' }} />
        <button type="submit" disabled={isSending}
          style={{ padding: '10px 18px', border: 'none', borderRadius: '6px', background: '#8b5cf6', color: '#fff', cursor: 'pointer' }}>
          보내기
        </button>
      </form>
    </div>
  )
}
