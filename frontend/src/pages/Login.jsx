import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Phone, Lock, Eye, EyeOff, Loader2 } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import Modal from '../components/Modal'
import lariLogo from '../assets/lari-traders-logo.png'

export default function Login() {
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [shake, setShake] = useState(false)

  // Change password modal
  const [showChangePassword, setShowChangePassword] = useState(false)
  const [currentPwd, setCurrentPwd] = useState('')
  const [newPwd, setNewPwd] = useState('')
  const [changingPwd, setChangingPwd] = useState(false)

  const { login, changePassword } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const data = await login(phone, password)
      if (data.mustChangePassword) {
        setShowChangePassword(true)
        setCurrentPwd(password)
      } else {
        toast.success(`Welcome back, ${data.name}!`)
        navigate('/')
      }
    } catch (err) {
      const msg = err.response?.data?.error || err.message || 'Invalid credentials'
      setError(msg)
      setShake(true)
      setTimeout(() => setShake(false), 600)
    } finally {
      setLoading(false)
    }
  }

  const handleChangePassword = async (e) => {
    e.preventDefault()
    const pwdRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,72}$/;
    if (!pwdRegex.test(newPwd)) {
      toast.error('Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character, and be between 8 and 72 characters.');
      return;
    }
    setChangingPwd(true)
    try {
      await changePassword(currentPwd, newPwd)
      toast.success('Password changed successfully!')
      setShowChangePassword(false)
      navigate('/')
    } catch {
      toast.error('Failed to change password')
    } finally {
      setChangingPwd(false)
    }
  }

  return (
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'var(--color-bg)',
      backgroundImage: 'radial-gradient(ellipse at 30% 20%, rgba(245,158,11,0.06) 0%, transparent 50%), radial-gradient(ellipse at 70% 80%, rgba(59,130,246,0.04) 0%, transparent 50%)',
      padding: 'var(--space-4)',
    }}>
      <motion.div
        initial={{ opacity: 0, y: 30, scale: 0.95 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
        style={{ width: '100%', maxWidth: 420 }}
      >
        {/* Logo */}
        <motion.div
          style={{ textAlign: 'center', marginBottom: 'var(--space-6)' }}
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
        >
          <motion.div
            animate={{
              filter: [
                'drop-shadow(0 0 8px rgba(139,26,26,0.15))',
                'drop-shadow(0 0 20px rgba(139,26,26,0.30))',
                'drop-shadow(0 0 8px rgba(139,26,26,0.15))',
              ],
            }}
            transition={{ duration: 3, repeat: Infinity }}
            style={{
              display: 'inline-block',
              marginBottom: 'var(--space-2)',
            }}
          >
            <img
              src={lariLogo}
              alt="Lari Traders"
              style={{
                width: 240,
                height: 'auto',
                display: 'block',
              }}
            />
          </motion.div>
          <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-muted)', marginTop: 'var(--space-1)' }}>
            Sign in to your account
          </p>
        </motion.div>

        {/* Login Card */}
        <motion.div
          className="card card-glass"
          animate={shake ? { x: [0, -12, 12, -8, 8, -4, 4, 0] } : {}}
          transition={{ duration: 0.5 }}
          style={{ padding: 'var(--space-8)' }}
        >
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
            {/* Phone */}
            <div className="form-group">
              <label className="form-label">Phone Number</label>
              <div style={{ position: 'relative' }}>
                <Phone size={18} style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)', color: 'var(--color-text-muted)' }} />
                <input
                  id="login-phone"
                  className="form-input"
                  type="tel"
                  placeholder="Enter 10-digit phone"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value.replace(/\D/g, '').slice(0, 10))}
                  maxLength={10}
                  required
                  autoComplete="tel"
                  style={{ paddingLeft: 40 }}
                />
              </div>
            </div>

            {/* Password */}
            <div className="form-group">
              <label className="form-label">Password</label>
              <div style={{ position: 'relative' }}>
                <Lock size={18} style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)', color: 'var(--color-text-muted)' }} />
                <input
                  id="login-password"
                  className="form-input"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  maxLength={72}
                  autoComplete="current-password"
                  style={{ paddingLeft: 40, paddingRight: 44 }}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  style={{
                    position: 'absolute', right: 8, top: '50%', transform: 'translateY(-50%)',
                    background: 'none', border: 'none', cursor: 'pointer',
                    color: 'var(--color-text-muted)', padding: 4,
                  }}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            {/* Error */}
            {error && (
              <motion.div
                initial={{ opacity: 0, y: -4 }}
                animate={{ opacity: 1, y: 0 }}
                style={{
                  padding: 'var(--space-3) var(--space-4)',
                  background: 'var(--color-danger-soft)',
                  color: 'var(--color-danger)',
                  borderRadius: 'var(--radius-md)',
                  fontSize: 'var(--font-size-sm)',
                  fontWeight: 'var(--font-weight-medium)',
                }}
              >
                {error}
              </motion.div>
            )}

            {/* Submit */}
            <motion.button
              type="submit"
              className="btn btn-primary btn-lg w-full"
              disabled={loading || phone.length !== 10 || !password}
              whileTap={{ scale: 0.97 }}
              style={{ marginTop: 'var(--space-2)' }}
            >
              {loading ? (
                <>
                  <Loader2 size={20} className="spinner" style={{ borderColor: 'transparent', borderTopColor: 'var(--color-text-inverse)' }} />
                  Signing in...
                </>
              ) : (
                'Sign In'
              )}
            </motion.button>
          </form>
        </motion.div>

        <p style={{ textAlign: 'center', marginTop: 'var(--space-6)', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
          © {new Date().getFullYear()} Lari Traders. All rights reserved.
        </p>
      </motion.div>

      {/* Change Password Modal */}
      <Modal isOpen={showChangePassword} onClose={() => {}} title="Change Password Required">
        <p style={{ color: 'var(--color-text-secondary)', marginBottom: 'var(--space-5)', fontSize: 'var(--font-size-base)' }}>
          You must change your default password before continuing.
        </p>
        <form onSubmit={handleChangePassword} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div className="form-group">
            <label className="form-label">New Password</label>
            <input
              id="new-password"
              className="form-input"
              type="password"
              placeholder="Min 8 chars, Upper, Lower, Digit, Special"
              value={newPwd}
              onChange={(e) => setNewPwd(e.target.value)}
              required
              minLength={8}
              maxLength={72}
            />
          </div>
          <div className="form-actions" style={{ borderTop: 'none', marginTop: 0, paddingTop: 0 }}>
            <motion.button
              type="submit"
              className="btn btn-primary"
              disabled={changingPwd || newPwd.length < 8}
              whileTap={{ scale: 0.97 }}
            >
              {changingPwd ? 'Changing...' : 'Change Password'}
            </motion.button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
