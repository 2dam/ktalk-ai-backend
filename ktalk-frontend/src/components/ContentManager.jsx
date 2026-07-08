import { useState, useEffect, useRef } from 'react'
import axios from 'axios'
import { API_URL, AI_URL } from '../api'

function ContentManager() {
  const [contents, setContents] = useState([])
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    dialogue: '',
    category: 'korean',
    koreanLevel: 'beginner'
  })
  const [editingId, setEditingId] = useState(null)
  const [aiTopic, setAiTopic] = useState('')
  const [isGenerating, setIsGenerating] = useState(false)
  const [speakingId, setSpeakingId] = useState(null)
  const [showDialogueId, setShowDialogueId] = useState(null)
  const [swapVoices, setSwapVoices] = useState(false)
  const audioRef = useRef(null)
  const speakRequestIdRef = useRef(0)

  useEffect(() => {
    fetchContents()
  }, [])

  const fetchContents = async () => {
    try {
      const response = await axios.get(API_URL)
      setContents(response.data.data || response.data)
    } catch (error) {
      console.error('Error:', error)
    }
  }

  const handleCreate = async (e) => {
    e.preventDefault()
    try {
      await axios.post(API_URL, formData, {
        headers: { 'Content-Type': 'application/json; charset=utf-8' }
      })
      setFormData({ title: '', description: '', dialogue: '', category: 'korean', koreanLevel: 'beginner' })
      fetchContents()
    } catch (error) {
      console.error('Error:', error)
    }
  }

  const handleUpdate = async (id) => {
    try {
      await axios.put(API_URL + '/' + id, formData, {
        headers: { 'Content-Type': 'application/json; charset=utf-8' }
      })
      setEditingId(null)
      setFormData({ title: '', description: '', dialogue: '', category: 'korean', koreanLevel: 'beginner' })
      fetchContents()
    } catch (error) {
      console.error('Error:', error)
    }
  }

  const handleDelete = async (id) => {
    if (window.confirm('정말 삭제하시겠습니까?')) {
      try {
        await axios.delete(API_URL + '/' + id)
        fetchContents()
      } catch (error) {
        console.error('Error:', error)
      }
    }
  }

  const loadForEdit = (content) => {
    setEditingId(content.id)
    setFormData({
      title: content.title,
      description: content.description,
      dialogue: content.dialogue || '',
      category: content.category,
      koreanLevel: content.koreanLevel
    })
  }

  const handleCancel = () => {
    setEditingId(null)
    setFormData({ title: '', description: '', dialogue: '', category: 'korean', koreanLevel: 'beginner' })
  }

  const handleAIGenerate = async () => {
    if (!aiTopic.trim()) {
      alert('주제를 입력해주세요!')
      return
    }

    setIsGenerating(true)
    try {
      const response = await axios.post(AI_URL + '/generate', { topic: aiTopic }, {
        headers: { 'Content-Type': 'application/json; charset=utf-8' }
      })

      if (response.data.success) {
        alert('AI가 콘텐츠를 생성했습니다!')
        setAiTopic('')
        fetchContents()
      }
    } catch (error) {
      alert('AI 콘텐츠 생성 실패: ' + (error.response?.data?.message || error.message))
    } finally {
      setIsGenerating(false)
    }
  }

  const handleShowDialogueAndSpeak = async (content) => {
    // 대화문이 있으면 바로 표시
    if (content.dialogue) {
      if (showDialogueId === content.id) {
        stopSpeaking()
      } else {
        setShowDialogueId(content.id)
        handleSpeak(content)
      }
      return
    }

    // 대화문이 없으면 AI가 자동 생성
    const confirmGenerate = window.confirm(
        '대화문이 없습니다. AI가 자동으로 생성하시겠습니까?'
    )

    if (!confirmGenerate) return

    try {
      const response = await axios.post(
          `${AI_URL}/dialogue/${content.id}`,
          {},
          {
            headers: { 'Content-Type': 'application/json; charset=utf-8' }
          }
      )

      if (response.data.success) {
        alert('AI가 대화문을 생성했습니다!')
        await fetchContents() // 콘텐츠 목록 새로고침

        // 새로고침 후 대화문 표시
        setShowDialogueId(content.id)

        // 업데이트된 콘텐츠 찾기
        const updatedContent = contents.find(c => c.id === content.id)
        if (updatedContent && updatedContent.dialogue) {
          handleSpeak(updatedContent)
        }
      }
    } catch (error) {
      alert('대화문 생성 실패: ' + (error.response?.data?.message || error.message))
    }
  }

  const handleSpeak = async (content, swap = swapVoices) => {
    const requestId = ++speakRequestIdRef.current

    if (audioRef.current) {
      audioRef.current.pause()
      audioRef.current = null
    }

    try {
      setSpeakingId(content.id)

      const response = await axios.post(`${AI_URL}/tts/dialogue`, {
        title: content.title,
        description: content.description,
        dialogue: content.dialogue || '',
        swap
      })

      // 응답을 기다리는 동안 다른 요청(중지/재선택)이 있었다면 이 결과는 버린다 (중복 재생 방지)
      if (requestId !== speakRequestIdRef.current) return

      const segments = response.data.data.segments

      for (const segment of segments) {
        if (requestId !== speakRequestIdRef.current) return

        const audio = new Audio(`data:audio/mp3;base64,${segment.audioContent}`)
        audioRef.current = audio

        await new Promise((resolve) => {
          audio.onended = resolve
          audio.onerror = resolve
          audio.play().catch(resolve)
        })

        if (requestId !== speakRequestIdRef.current) return
      }

      setSpeakingId(null)
      audioRef.current = null
    } catch (error) {
      if (requestId !== speakRequestIdRef.current) return
      alert('음성 생성 실패: ' + (error.response?.data?.message || error.message))
      setSpeakingId(null)
    }
  }

  const handleToggleSwapVoices = (content) => {
    const nextSwap = !swapVoices
    setSwapVoices(nextSwap)
    handleSpeak(content, nextSwap)
  }

  const stopSpeaking = () => {
    speakRequestIdRef.current++
    if (audioRef.current) {
      audioRef.current.pause()
      audioRef.current = null
    }
    setSpeakingId(null)
    setShowDialogueId(null)
  }

  return (
      <div>
        <div style={{ marginBottom: '30px', padding: '20px', border: '2px solid #007bff', borderRadius: '8px', backgroundColor: '#f0f8ff' }}>
          <h2>🤖 AI로 콘텐츠 생성 <span style={{ fontSize: '13px', color: '#999', fontWeight: 'normal' }}>AI Content Generation</span></h2>
          <p>
            주제를 입력하면 AI가 자동으로 한국어 학습 콘텐츠를 생성합니다.
            <br />
            <span style={{ fontSize: '13px', color: '#999' }}>Enter a topic and AI will automatically generate Korean learning content.</span>
          </p>
          <div style={{ marginTop: '15px' }}>
            <label style={{ fontSize: '13px', color: '#999', display: 'block', marginBottom: '4px' }}>
              주제 <span>Topic</span>
            </label>
          <div style={{ display: 'flex', gap: '10px' }}>
            <input
                type="text"
                placeholder="예: 인사, 문법, 여행, 비즈니스"
                value={aiTopic}
                onChange={(e) => setAiTopic(e.target.value)}
                style={{ flex: 1, padding: '10px', fontSize: '16px' }}
                disabled={isGenerating}
            />
            <button
                onClick={handleAIGenerate}
                disabled={isGenerating}
                style={{
                  padding: '10px 20px',
                  fontSize: '16px',
                  cursor: isGenerating ? 'not-allowed' : 'pointer',
                  backgroundColor: isGenerating ? '#ccc' : '#007bff',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px'
                }}
            >
              {isGenerating ? '생성 중...' : 'AI 생성'}
            </button>
          </div>
          </div>
        </div>

        <form onSubmit={editingId ? function() { handleUpdate(editingId) } : handleCreate} style={{ marginBottom: '30px', padding: '20px', border: '1px solid #ddd', borderRadius: '8px' }}>
          <h2>
            {editingId ? '콘텐츠 수정' : '새 콘텐츠 추가'}{' '}
            <span style={{ fontSize: '13px', color: '#999', fontWeight: 'normal' }}>
              {editingId ? 'Edit Content' : 'Add New Content'}
            </span>
          </h2>

          <div style={{ marginBottom: '15px' }}>
            <label style={{ fontSize: '13px', color: '#999', display: 'block', marginBottom: '4px' }}>
              제목 <span>Title</span>
            </label>
            <input
                type="text"
                placeholder="제목"
                value={formData.title}
                onChange={function(e) { setFormData(Object.assign({}, formData, { title: e.target.value })) }}
                style={{ width: '100%', padding: '10px', fontSize: '16px' }}
                required
            />
          </div>

          <div style={{ marginBottom: '15px' }}>
            <label style={{ fontSize: '13px', color: '#999', display: 'block', marginBottom: '4px' }}>
              설명 <span>Description</span>
            </label>
          <textarea
              placeholder="설명"
              value={formData.description}
              onChange={function(e) { setFormData(Object.assign({}, formData, { description: e.target.value })) }}
              style={{ width: '100%', padding: '10px', fontSize: '16px', minHeight: '100px' }}
              required
          />
          </div>

          <div style={{ marginBottom: '15px' }}>
            <label style={{ fontSize: '13px', color: '#999', display: 'block', marginBottom: '4px' }}>
              회화/대화문 (줄바꿈으로 구분) <span>Dialogue (separate by line breaks)</span>
            </label>
          <textarea
              placeholder="회화/대화문 (줄바꿈으로 구분)"
              value={formData.dialogue}
              onChange={function(e) { setFormData(Object.assign({}, formData, { dialogue: e.target.value })) }}
              style={{ width: '100%', padding: '10px', fontSize: '16px', minHeight: '150px' }}
          />
          </div>

          <div style={{ marginBottom: '15px' }}>
            <label style={{ fontSize: '13px', color: '#999', display: 'block', marginBottom: '4px' }}>
              카테고리 <span>Category</span>
            </label>
            <select
                value={formData.category}
                onChange={function(e) { setFormData(Object.assign({}, formData, { category: e.target.value })) }}
                style={{ width: '100%', padding: '10px', fontSize: '16px' }}
            >
              <option value="korean">한국어 (Korean)</option>
              <option value="culture">문화 (Culture)</option>
              <option value="grammar">문법 (Grammar)</option>
            </select>
          </div>

          <div style={{ marginBottom: '15px' }}>
            <label style={{ fontSize: '13px', color: '#999', display: 'block', marginBottom: '4px' }}>
              레벨 <span>Level</span>
            </label>
            <select
                value={formData.koreanLevel}
                onChange={function(e) { setFormData(Object.assign({}, formData, { koreanLevel: e.target.value })) }}
                style={{ width: '100%', padding: '10px', fontSize: '16px' }}
            >
              <option value="beginner">초급 (Beginner)</option>
              <option value="intermediate">중급 (Intermediate)</option>
              <option value="advanced">고급 (Advanced)</option>
            </select>
          </div>

          <div>
            <button type="submit" style={{ padding: '10px 20px', marginRight: '10px', cursor: 'pointer' }}>
              {editingId ? '수정 Edit' : '추가 Add'}
            </button>
            {editingId && (
                <button type="button" onClick={handleCancel} style={{ padding: '10px 20px', cursor: 'pointer' }}>
                  취소 Cancel
                </button>
            )}
          </div>
        </form>

        <div>
          <h2>콘텐츠 목록 ({contents.length}개)</h2>
          {contents.length === 0 ? (
              <p>등록된 콘텐츠가 없습니다.</p>
          ) : (
              contents.map(function(content) {
                return (
                    <div
                        key={content.id}
                        style={{
                          padding: '20px',
                          marginBottom: '15px',
                          border: '1px solid #ddd',
                          borderRadius: '8px',
                          backgroundColor: '#f9f9f9'
                        }}
                    >
                      <h3>{content.title}</h3>
                      <p>{content.description}</p>
                      <div style={{ color: '#666', fontSize: '14px', marginTop: '10px' }}>
                        <span>카테고리: {content.category}</span> |
                        <span> 레벨: {content.koreanLevel}</span>
                      </div>
                      <div style={{ marginTop: '10px' }}>
                        <button
                            onClick={function() { loadForEdit(content) }}
                            style={{ padding: '5px 15px', marginRight: '10px', cursor: 'pointer' }}
                        >
                          수정
                        </button>
                        <button
                            onClick={function() { handleDelete(content.id) }}
                            style={{ padding: '5px 15px', marginRight: '10px', cursor: 'pointer', backgroundColor: '#ff4444', color: 'white', border: 'none' }}
                        >
                          삭제
                        </button>
                        <button
                            onClick={function() { handleShowDialogueAndSpeak(content) }}
                            style={{
                              padding: '5px 15px',
                              cursor: 'pointer',
                              backgroundColor: showDialogueId === content.id ? '#28a745' : '#17a2b8',
                              color: 'white',
                              border: 'none',
                              borderRadius: '4px',
                              marginRight: '10px'
                            }}
                        >
                          {showDialogueId === content.id ? '💬 닫기' : '💬 대화'}
                        </button>
                        {showDialogueId === content.id && (
                            <button
                                onClick={stopSpeaking}
                                style={{
                                  padding: '5px 15px',
                                  cursor: 'pointer',
                                  backgroundColor: '#dc3545',
                                  color: 'white',
                                  border: 'none',
                                  borderRadius: '4px',
                                  marginRight: '10px'
                                }}
                            >
                              🔇 중지
                            </button>
                        )}
                        {showDialogueId === content.id && (
                            <button
                                onClick={function() { handleToggleSwapVoices(content) }}
                                style={{
                                  padding: '5px 15px',
                                  cursor: 'pointer',
                                  backgroundColor: '#6c5ce7',
                                  color: 'white',
                                  border: 'none',
                                  borderRadius: '4px'
                                }}
                            >
                              🔄 {swapVoices ? 'A: 여성 / B: 남성' : 'A: 남성 / B: 여성'}
                            </button>
                        )}
                      </div>

                      {showDialogueId === content.id && content.dialogue && (
                          <div style={{
                            marginTop: '20px',
                            padding: '20px',
                            backgroundColor: '#fff',
                            border: '2px solid #17a2b8',
                            borderRadius: '8px',
                            whiteSpace: 'pre-line',
                            lineHeight: '2',
                            fontSize: '16px'
                          }}>
                            <div style={{
                              marginBottom: '15px',
                              fontWeight: 'bold',
                              color: '#17a2b8',
                              fontSize: '18px'
                            }}>
                              💬 대화문 (음성 재생 중)
                            </div>
                            <div style={{
                              backgroundColor: '#f8f9fa',
                              padding: '15px',
                              borderRadius: '4px',
                              borderLeft: '4px solid #17a2b8'
                            }}>
                              {content.dialogue}
                            </div>
                          </div>
                      )}
                    </div>
                )
              })
          )}
        </div>
      </div>
  )
}

export default ContentManager
