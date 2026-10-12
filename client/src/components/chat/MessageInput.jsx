import { useId, useRef, useState } from 'react';
import { Button, Input } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import { getMessageSendError, getMessageValidationError, MAX_MESSAGE_LENGTH } from '../../utils/messageInput';

// A channel change remounts the draft so an in-flight result cannot clear another channel's input.
export default function MessageInput(props) {
  return <ChannelMessageInput key={props.channelId ?? 'no-channel'} {...props} />;
}

function ChannelMessageInput({ channelId, channelName, onSend, onSent, disabled = false }) {
  const [content, setContent] = useState('');
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState(null);
  const inFlight = useRef(false);
  const inputRef = useRef(null);
  const composing = useRef(false);
  const hintId = useId();
  const errorId = useId();
  const unavailable = disabled || channelId == null || typeof onSend !== 'function';
  const validationError = getMessageValidationError(content);
  const visibleError = sendError || (content.length > MAX_MESSAGE_LENGTH ? validationError : null);

  async function handleSend() {
    if (unavailable || inFlight.current || validationError) return;
    inFlight.current = true;
    setSending(true);
    setSendError(null);
    let sentMessage;
    try {
      // Pass the original content and channel separately; never accept a client-supplied sender.
      sentMessage = await onSend(channelId, { content });
    } catch (error) {
      setSendError(getMessageSendError(error));
      return;
    } finally {
      inFlight.current = false;
      setSending(false);
      inputRef.current?.focus();
    }
    setContent('');
    onSent?.(sentMessage, channelId);
  }

  function handleKeyDown(event) {
    if (event.key !== 'Enter' || event.shiftKey || composing.current
      || event.nativeEvent.isComposing || event.keyCode === 229) return;
    event.preventDefault();
    handleSend();
  }

  return (
    <div className="message-composer">
      <div className={`message-composer__box${visibleError ? ' message-composer__box--error' : ''}`}>
        <Input.TextArea
          ref={inputRef}
          aria-label="Nội dung tin nhắn"
          aria-describedby={`${hintId}${visibleError ? ` ${errorId}` : ''}`}
          aria-invalid={Boolean(visibleError)}
          value={content}
          onChange={(event) => { setContent(event.target.value); setSendError(null); }}
          onKeyDown={handleKeyDown}
          onCompositionStart={() => { composing.current = true; }}
          onCompositionEnd={() => { composing.current = false; }}
          placeholder={unavailable ? 'Chọn kênh để bắt đầu trò chuyện' : `Nhắn tin${channelName ? ` trong #${channelName}` : ''}…`}
          disabled={unavailable}
          readOnly={sending}
          autoSize={{ minRows: 1, maxRows: 5 }}
          variant="borderless"
        />
        <Button
          type="primary"
          aria-label="Gửi tin nhắn"
          icon={<SendOutlined />}
          onClick={handleSend}
          disabled={unavailable || Boolean(validationError) || sending}
          loading={sending}
        >
          <span className="message-composer__send-label">Gửi</span>
        </Button>
      </div>
      {visibleError && <p className="message-composer__error" id={errorId} role="alert">{visibleError}</p>}
      <div className="message-composer__help" id={hintId}>
        <span>{unavailable ? 'Bạn có thể gửi tin nhắn sau khi chọn một kênh.' : <><kbd>Enter</kbd> gửi · <kbd>Shift + Enter</kbd> xuống dòng</>}</span>
        {!unavailable && <span aria-label={`${content.length} trên ${MAX_MESSAGE_LENGTH} đơn vị UTF-16`}>{content.length} / {MAX_MESSAGE_LENGTH}</span>}
      </div>
    </div>
  );
}
