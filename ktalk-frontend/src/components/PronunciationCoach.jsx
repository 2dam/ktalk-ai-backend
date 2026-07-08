import { useRef, useState } from 'react'
import axios from 'axios'
import { AI_URL } from '../api'

const SAMPLE_PHRASES = [
  '안녕하세요, 만나서 반가워요.',
  '오늘 하루도 힘내세요.',
  '보고 싶었어요.',
  '같이 가도 될까요?',
  '정말 고마워요, 잊지 않을게요.'
]

function PronunciationCoach() {
  const [targetText, setTargetText] = useState(SAMPLE_PHRASES[0])
  const [isRecording, setIsRecording] = useState(false)
  const [audioBlob, setAudioBlob] = useState(null)
  const [audioUrl, setAudioUrl] = useState(null)
  const [isEvaluating, setIsEvaluating] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')

  const mediaRecorderRef = useRef(null)
  const chunksRef = useRef([])

  const speakTarget = () => {
    if (!('speechSynthesis' in window)) return
    window.speechSynthesis.cancel()
    const utterance = new SpeechSynthesisUtterance(targetText)
    utterance.lang = 'ko-KR'
    utterance.rate = 0.8
    window.speechSynthesis.speak(utterance)
  }

  const startRecording = async () => {
    setError('')
    setResult(null)
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const mediaRecorder = new MediaRecorder(stream)
      chunksRef.current = []

      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data)
      }
      mediaRecorder.onstop = () => {
        const blob = new Blob(chunksRef.current, { type: 'audio/webm' })
        setAudioBlob(blob)
        setAudioUrl(URL.createObjectURL(blob))
        stream.getTracks().forEach((track) => track.stop())
      }

      mediaRecorder.start()
      mediaRecorderRef.current = mediaRecorder
      setIsRecording(true)
    } catch (err) {
      setError('마이크 접근에 실패했습니다. 브라우저에서 마이크 권한을 허용해주세요.')
    }
  }

  const stopRecording = () => {
    mediaRecorderRef.current?.stop()
    setIsRecording(false)
  }

  const handleEvaluate = async () => {
    if (!audioBlob) return
    setIsEvaluating(true)
    setError('')
    setResult(null)

    const formData = new FormData()
    formData.append('audio', audioBlob, 'recording.webm')
    formData.append('targetText', targetText)

    try {
      const response = await axios.post(`${AI_URL}/pronunciation-coach`, formData)
      if (response.data.success) {
        setResult(response.data.data)
      } else {
        setError(response.data.message || '발음 평가에 실패했습니다.')
      }
    } catch (err) {
      setError(
          err.response?.data?.message ||
          '발음 평가에 실패했습니다. Gemini API 키가 설정되어 있는지 확인해주세요.'
      )
    } finally {
      setIsEvaluating(false)
    }
  }

  return (
      <div>
        <div style={{ padding: '20px', border: '2px solid #fd7e14', borderRadius: '8px', backgroundColor: '#fff8f0', marginBottom: '20px' }}>
          <h2>🎤 AI Pronunciation Coach</h2>
          <p>목표 문장을 듣고 따라 말한 뒤 녹음하면, AI가 발음을 채점하고 개선 팁을 알려줍니다.</p>
        </div>

        <div style={{ padding: '20px', border: '1px solid #ddd', borderRadius: '8px', marginBottom: '20px' }}>
          <label style={{ display: 'block', marginBottom: '8px', fontWeight: 'bold' }}>목표 문장 선택</label>
          <select
              value={targetText}
              onChange={(e) => { setTargetText(e.target.value); setResult(null); setAudioBlob(null); setAudioUrl(null) }}
              style={{ width: '100%', padding: '10px', fontSize: '16px', marginBottom: '10px' }}
          >
            {SAMPLE_PHRASES.map((phrase) => (
                <option key={phrase} value={phrase}>{phrase}</option>
            ))}
          </select>

          <div style={{ fontSize: '22px', margin: '15px 0', textAlign: 'center' }}>
            {targetText}
            <button onClick={speakTarget} style={{ marginLeft: '10px', border: 'none', background: 'none', cursor: 'pointer', fontSize: '20px' }}>
              🔊
            </button>
          </div>

          <div style={{ display: 'flex', gap: '10px', justifyContent: 'center', marginTop: '15px' }}>
            {!isRecording ? (
                <button onClick={startRecording}
                        style={{ padding: '12px 24px', cursor: 'pointer', backgroundColor: '#fd7e14', color: 'white', border: 'none', borderRadius: '24px', fontSize: '16px' }}>
                  🎙️ 녹음 시작
                </button>
            ) : (
                <button onClick={stopRecording}
                        style={{ padding: '12px 24px', cursor: 'pointer', backgroundColor: '#dc3545', color: 'white', border: 'none', borderRadius: '24px', fontSize: '16px' }}>
                  ⏹ 녹음 중지
                </button>
            )}
            {audioUrl && !isRecording && (
                <button onClick={handleEvaluate} disabled={isEvaluating}
                        style={{ padding: '12px 24px', cursor: 'pointer', backgroundColor: '#28a745', color: 'white', border: 'none', borderRadius: '24px', fontSize: '16px' }}>
                  {isEvaluating ? '평가 중...' : '✅ 평가받기'}
                </button>
            )}
          </div>

          {audioUrl && (
              <div style={{ marginTop: '15px', textAlign: 'center' }}>
                <audio controls src={audioUrl} />
              </div>
          )}

          {error && <p style={{ color: '#dc3545', marginTop: '15px' }}>⚠ {error}</p>}
        </div>

        {result && (
            <div style={{ padding: '20px', border: '2px solid #28a745', borderRadius: '8px', backgroundColor: '#f0fff4' }}>
              <h3>평가 결과</h3>
              <div style={{ fontSize: '36px', fontWeight: 'bold', color: '#28a745', margin: '10px 0' }}>
                {result.score}점
              </div>
              <p><strong>인식된 발화:</strong> {result.transcribedText}</p>
              <p><strong>총평:</strong> {result.feedback}</p>
              {result.tips?.length > 0 && (
                  <ul>
                    {result.tips.map((tip, idx) => <li key={idx}>{tip}</li>)}
                  </ul>
              )}
            </div>
        )}
      </div>
  )
}

export default PronunciationCoach
