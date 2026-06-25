import { useState } from 'react'
import axios from 'axios'

const API_URL = 'http://localhost:8080/api/auth'
const GOOGLE_LOGIN_URL = 'http://localhost:8080/oauth2/authorization/google'

function Auth({ onLogin }) {
    const [isLogin, setIsLogin] = useState(true)
    const [formData, setFormData] = useState({
        username: '',
        password: '',
        email: ''
    })

    const handleSubmit = async (e) => {
        e.preventDefault()
        try {
            const endpoint = isLogin ? '/login' : '/register'
            const response = await axios.post(`${API_URL}${endpoint}`, formData, {
                headers: { 'Content-Type': 'application/json; charset=utf-8' }
            })

            if (response.data.success) {
                alert(isLogin ? '로그인 성공!' : '회원가입 성공!')
                if (isLogin) {
                    if (response.data.token) {
                        localStorage.setItem('token', response.data.token)
                    }
                    onLogin(response.data.user)
                } else {
                    setIsLogin(true)
                    setFormData({ username: '', password: '', email: '' })
                }
            }
        } catch (error) {
            alert(error.response?.data?.message || '오류가 발생했습니다.')
        }
    }

    return (
        <div style={{ maxWidth: '400px', margin: '50px auto', padding: '30px', border: '1px solid #ddd', borderRadius: '8px' }}>
            <h2>{isLogin ? '로그인' : '회원가입'}</h2>

            <form onSubmit={handleSubmit}>
                <div style={{ marginBottom: '15px' }}>
                    <input
                        type="text"
                        placeholder="사용자명"
                        value={formData.username}
                        onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                        style={{ width: '100%', padding: '10px', fontSize: '16px' }}
                        required
                    />
                </div>

                {!isLogin && (
                    <div style={{ marginBottom: '15px' }}>
                        <input
                            type="email"
                            placeholder="이메일"
                            value={formData.email}
                            onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                            style={{ width: '100%', padding: '10px', fontSize: '16px' }}
                            required
                        />
                    </div>
                )}

                <div style={{ marginBottom: '15px' }}>
                    <input
                        type="password"
                        placeholder="비밀번호"
                        value={formData.password}
                        onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                        style={{ width: '100%', padding: '10px', fontSize: '16px' }}
                        required
                    />
                </div>

                <button type="submit" style={{ width: '100%', padding: '12px', fontSize: '16px', cursor: 'pointer', backgroundColor: '#007bff', color: 'white', border: 'none', borderRadius: '4px' }}>
                    {isLogin ? '로그인' : '회원가입'}
                </button>
            </form>

            <div style={{ textAlign: 'center', margin: '20px 0' }}>
                <a href={GOOGLE_LOGIN_URL} style={{
                    display: 'inline-block', width: '100%', padding: '12px', fontSize: '16px',
                    backgroundColor: '#fff', color: '#444', border: '1px solid #ddd',
                    borderRadius: '4px', textDecoration: 'none', boxSizing: 'border-box'
                }}>
                    🔐 Google로 로그인
                </a>
            </div>

            <p style={{ textAlign: 'center', marginTop: '20px' }}>
                {isLogin ? '계정이 없으신가요?' : '이미 계정이 있으신가요?'}
                <button
                    onClick={() => {
                        setIsLogin(!isLogin)
                        setFormData({ username: '', password: '', email: '' })
                    }}
                    style={{ marginLeft: '10px', cursor: 'pointer', background: 'none', border: 'none', color: '#007bff', textDecoration: 'underline' }}
                >
                    {isLogin ? '회원가입' : '로그인'}
                </button>
            </p>
        </div>
    )
}

export default Auth