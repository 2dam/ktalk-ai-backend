import { useState } from 'react'
import { createPortal } from 'react-dom'
import axios from 'axios'
import { Bi } from '../EnglishHint'
import { FEEDBACK_URL, authHeaders } from '../api'

const CATEGORIES = [
  { value: 'bug', ko: '불편/오류', en: 'Bug' },
  { value: 'idea', ko: '제안', en: 'Idea' },
  { value: 'other', ko: '기타', en: 'Other' },
]
const MAX_LENGTH = 2000

function FeedbackDialog({ onClose }) {
  const [category, setCategory] = useState('bug')
  const [message, setMessage] = useState('')
  const [status, setStatus] = useState('idle') // idle | sending | done | error
  const [error, setError] = useState('')

  const submit = async (event) => {
    event.preventDefault()
    if (!message.trim() || status === 'sending') return
    setStatus('sending')
    setError('')
    try {
      await axios.post(
        FEEDBACK_URL,
        { category, message: message.trim(), page: window.location.pathname },
        { headers: authHeaders() },
      )
      setStatus('done')
    } catch (err) {
      setStatus('error')
      setError(err.response?.data?.message || '보내지 못했어요. 잠시 후 다시 시도해 주세요.')
    }
  }

  return createPortal(
    <div className="ios-install-overlay" onClick={onClose}>
      <div className="ios-install-card feedback-card" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
        <button type="button" className="ios-install-close" onClick={onClose} aria-label="닫기">×</button>
        <h2><Bi en="Send feedback">의견 보내기</Bi></h2>

        {status === 'done' ? (
          <>
            <p className="feedback-thanks"><Bi en="Thank you! We read every message.">고마워요! 보내주신 의견은 꼼꼼히 읽어볼게요.</Bi></p>
            <button type="button" className="ios-install-ok" onClick={onClose}><Bi en="Close">닫기</Bi></button>
          </>
        ) : (
          <form onSubmit={submit}>
            <div className="install-tabs" role="radiogroup" aria-label="분류">
              {CATEGORIES.map((item) => (
                <button
                  key={item.value}
                  type="button"
                  role="radio"
                  aria-checked={category === item.value}
                  className={category === item.value ? 'on' : ''}
                  onClick={() => setCategory(item.value)}
                >
                  <Bi en={item.en}>{item.ko}</Bi>
                </button>
              ))}
            </div>
            <textarea
              className="feedback-text"
              value={message}
              maxLength={MAX_LENGTH}
              rows={6}
              placeholder="불편했던 점, 바라는 기능, 무엇이든 적어 주세요."
              onChange={(event) => setMessage(event.target.value)}
              autoFocus
            />
            <small className="feedback-count">{message.length} / {MAX_LENGTH}</small>
            {error && <p className="feedback-error">{error}</p>}
            <button type="submit" className="ios-install-ok" disabled={!message.trim() || status === 'sending'}>
              {status === 'sending' ? <Bi en="Sending…">보내는 중…</Bi> : <Bi en="Send">보내기</Bi>}
            </button>
          </form>
        )}
      </div>
    </div>,
    document.body,
  )
}

/** 헤더의 "의견 보내기" 버튼. 로그인 없이도 보낼 수 있다. */
function FeedbackButton() {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button type="button" className="login-button" onClick={() => setOpen(true)}>
        <Bi en="Feedback">의견 보내기</Bi>
      </button>
      {open && <FeedbackDialog onClose={() => setOpen(false)} />}
    </>
  )
}

export default FeedbackButton
