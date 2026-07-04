import { useState } from 'react'
import axios from 'axios'
import { AI_URL } from '../api'

function speak(text) {
  if (!('speechSynthesis' in window)) return
  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'ko-KR'
  utterance.rate = 0.9
  window.speechSynthesis.speak(utterance)
}

function CharacterChat() {
  const [input, setInput] = useState('')
  const [language, setLanguage] = useState('')
  const [messages, setMessages] = useState([])
  const [isSending, setIsSending] = useState(false)
  const [error, setError] = useState('')
  // key: "msgIdx-pIdx" -> 'loading' | 'error' | { url, title }
  const [videoLookup, setVideoLookup] = useState({})

  const handleFindVideo = async (msgIdx, pIdx, source) => {
    const key = `${msgIdx}-${pIdx}`
    setVideoLookup((prev) => ({ ...prev, [key]: 'loading' }))

    try {
      const response = await axios.get(`${AI_URL}/videos/search`, {
        params: { query: source, maxResults: 1, preferShort: true }
      })
      const video = response.data.data?.[0]
      if (video) {
        // window.open()은 카카오톡 인앱 브라우저 등 일부 환경에서 조용히 막혀서
        // 아무 반응이 없는 것처럼 보일 수 있다. 대신 실제 <a> 링크를 화면에 띄워서
        // 사용자가 직접 눌러 이동하게 하면 어떤 브라우저에서도 확실히 작동한다.
        setVideoLookup((prev) => ({
          ...prev,
          [key]: { url: `https://www.youtube.com/watch?v=${video.videoId}`, title: video.title }
        }))
      } else {
        setVideoLookup((prev) => ({ ...prev, [key]: 'error' }))
      }
    } catch (err) {
      setVideoLookup((prev) => ({ ...prev, [key]: 'error' }))
    }
  }

  const handleSend = async (e) => {
    e.preventDefault()
    const text = input.trim()
    if (!text) return

    const userMessage = { role: 'user', text }
    setMessages((prev) => [...prev, userMessage])
    setInput('')
    setIsSending(true)
    setError('')

    try {
      const response = await axios.post(`${AI_URL}/phrase-match`, {
        text,
        language: language || undefined
      }, {
        headers: { 'Content-Type': 'application/json; charset=utf-8' }
      })
      const data = response.data
      setMessages((prev) => [...prev, { role: 'character', data }])
    } catch (err) {
      setError(
          err.response?.data?.message ||
          'K-드라마 캐릭터가 응답하지 못했습니다. Gemini API 키가 설정되어 있는지 확인해주세요.'
      )
    } finally {
      setIsSending(false)
    }
  }

  return (
      <div>
        <div style={{ padding: '20px', border: '2px solid #17a2b8', borderRadius: '8px', backgroundColor: '#f0fbfd', marginBottom: '20px' }}>
          <h2>💬 AI Character Chat</h2>
          <p>하고 싶은 말을 입력하면, K-POP/K-드라마 속 실제 대사로 비슷한 한국어 표현을 찾아드려요.</p>
        </div>

        <div style={{ border: '1px solid #ddd', borderRadius: '8px', padding: '20px', minHeight: '200px', marginBottom: '15px' }}>
          {messages.length === 0 && (
              <p style={{ color: '#999' }}>예: "I miss you so much" 라고 입력해보세요.</p>
          )}
          {messages.map((msg, idx) => (
              <div key={idx} style={{ marginBottom: '15px' }}>
                {msg.role === 'user' ? (
                    <div style={{ textAlign: 'right' }}>
                      <span style={{
                        display: 'inline-block', padding: '10px 15px', borderRadius: '16px',
                        backgroundColor: '#007bff', color: 'white', maxWidth: '70%'
                      }}>
                        {msg.text}
                      </span>
                    </div>
                ) : (
                    <div>
                      <div style={{ fontSize: '12px', color: '#999', marginBottom: '5px' }}>
                        감지된 언어: {msg.data.detectedLanguage}
                      </div>
                      {msg.data.phrases?.map((phrase, pIdx) => (
                          <div key={pIdx} style={{
                            padding: '12px 15px', marginBottom: '8px', borderRadius: '12px',
                            backgroundColor: '#e9ecef', maxWidth: '80%'
                          }}>
                            <div style={{ fontSize: '18px', fontWeight: 'bold' }}>
                              {phrase.korean}
                              <button onClick={() => speak(phrase.korean)}
                                      style={{ marginLeft: '8px', border: 'none', background: 'none', cursor: 'pointer' }}>
                                🔊
                              </button>
                            </div>
                            <div style={{ fontSize: '13px', color: '#666' }}>{phrase.romanization}</div>
                            <div style={{ fontSize: '14px', marginTop: '4px' }}>{phrase.meaning}</div>
                            <div style={{ fontSize: '12px', color: '#17a2b8', marginTop: '4px' }}>
                              🎵 {phrase.source} ({phrase.sourceType})
                            </div>
                            <div style={{ fontSize: '12px', color: '#999', marginTop: '4px' }}>
                              {phrase.usageContext}
                            </div>
                            <div style={{ marginTop: '8px' }}>
                              {(() => {
                                const found = videoLookup[`${idx}-${pIdx}`]
                                if (found && typeof found === 'object') {
                                  return (
                                      <a href={found.url} target="_blank" rel="noopener noreferrer"
                                         style={{
                                           display: 'inline-block', padding: '4px 10px', fontSize: '12px',
                                           backgroundColor: '#dc3545', color: 'white', border: '1px solid #dc3545',
                                           borderRadius: '12px', textDecoration: 'none'
                                         }}>
                                        ▶ 유튜브에서 열기
                                      </a>
                                  )
                                }
                                return (
                                    <button
                                        onClick={() => handleFindVideo(idx, pIdx, phrase.source)}
                                        disabled={found === 'loading'}
                                        style={{
                                          padding: '4px 10px', fontSize: '12px', cursor: 'pointer',
                                          backgroundColor: '#fff', color: '#dc3545', border: '1px solid #dc3545',
                                          borderRadius: '12px'
                                        }}>
                                      {found === 'loading' ? '검색 중...' : '🎬 유튜브에서 보기'}
                                    </button>
                                )
                              })()}
                              {videoLookup[`${idx}-${pIdx}`] === 'error' && (
                                  <span style={{ marginLeft: '8px', fontSize: '12px', color: '#dc3545' }}>
                                    영상을 찾지 못했습니다
                                  </span>
                              )}
                            </div>
                          </div>
                      ))}
                    </div>
                )}
              </div>
          ))}
          {isSending && <p style={{ color: '#999' }}>캐릭터가 표현을 찾는 중...</p>}
          {error && <p style={{ color: '#dc3545' }}>⚠ {error}</p>}
        </div>

        <form onSubmit={handleSend} style={{ display: 'flex', gap: '10px' }}>
          <select value={language} onChange={(e) => setLanguage(e.target.value)} style={{ padding: '10px' }}>
            <option value="">자동 감지</option>
            <option value="en">English</option>
            <option value="ja">日本語</option>
            <option value="zh">中文</option>
            <option value="es">Español</option>
          </select>
          <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="하고 싶은 말을 입력하세요..."
              style={{ flex: 1, padding: '10px', fontSize: '16px' }}
              disabled={isSending}
          />
          <button type="submit" disabled={isSending}
                  style={{ padding: '10px 20px', cursor: 'pointer', backgroundColor: '#17a2b8', color: 'white', border: 'none', borderRadius: '4px' }}>
            보내기
          </button>
        </form>
      </div>
  )
}

export default CharacterChat
