import { CheckCircle2, AlertCircle, X, Info } from 'lucide-react';
import { useEffect } from 'react';

interface ToastProps {
  message: string;
  type: 'success' | 'error' | 'warning' | 'info';
  onClose: () => void;
  duration?: number;
}

export function Toast({ message, type, onClose, duration = 4000 }: ToastProps) {
  useEffect(() => {
    const timer = setTimeout(() => {
      onClose();
    }, duration);
    return () => clearTimeout(timer);
  }, [duration, onClose]);

  const styles = {
    success: { bg: 'bg-green-50', border: 'border-green-200', text: 'text-green-800', icon: 'text-green-500', Icon: CheckCircle2 },
    error: { bg: 'bg-red-50', border: 'border-red-200', text: 'text-red-800', icon: 'text-red-500', Icon: AlertCircle },
    warning: { bg: 'bg-yellow-50', border: 'border-yellow-200', text: 'text-yellow-800', icon: 'text-yellow-500', Icon: AlertCircle },
    info: { bg: 'bg-blue-50', border: 'border-blue-200', text: 'text-blue-800', icon: 'text-blue-500', Icon: Info },
  };

  const currentStyle = styles[type];
  const { Icon } = currentStyle;

  return (
    <div className={`fixed top-6 right-6 z-[100] flex items-center justify-between p-4 border rounded-lg shadow-xl ${currentStyle.bg} ${currentStyle.border} ${currentStyle.text} min-w-[320px] max-w-md animate-in slide-in-from-top-4 fade-in duration-300`}>
      <div className="flex items-start gap-3">
        <Icon className={`w-5 h-5 mt-0.5 shrink-0 ${currentStyle.icon}`} />
        <div className="text-sm font-medium whitespace-pre-wrap">{message}</div>
      </div>
      <button 
        onClick={onClose} 
        className="p-1 hover:bg-black/5 rounded-full transition-colors ml-4 shrink-0"
        aria-label="Close"
      >
        <X className="w-4 h-4" />
      </button>
    </div>
  );
}
