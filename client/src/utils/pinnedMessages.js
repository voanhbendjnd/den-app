/**
 * Utilities for Pinned Messages (Zalo-style banner on Discord dark theme)
 */

export function truncatePinnedContent(content, maxLength = 80) {
  if (!content || typeof content !== 'string') return '';
  const clean = content.trim().replace(/\s+/g, ' ');
  if (clean.length <= maxLength) return clean;
  return clean.slice(0, maxLength) + '...';
}

export function formatPinnedTitle(authorName) {
  const author = (authorName || 'Thành viên').trim();
  return `${author} · Tin nhắn đã ghim`;
}

export function formatPinnedSummary(authorName, content, maxLength = 80) {
  const author = (authorName || 'Thành viên').trim();
  const truncated = truncatePinnedContent(content, maxLength);
  if (!truncated) return author;
  return `${author}: ${truncated}`;
}

export function canUnpinMessage(isOwner) {
  return Boolean(isOwner);
}

