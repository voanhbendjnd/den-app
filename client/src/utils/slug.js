import slugify from 'slugify';

/**
 * Converts a room name into a URL-safe slug.
 * Example: "Backend Team 01" -> "backend-team-01"
 */
export function toSlug(name) {
  return slugify(name, {
    lower: true,
    strict: true,
    trim: true,
  });
}
