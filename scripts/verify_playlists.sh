#!/usr/bin/env bash
# Live 6-playlist count check. Reads the key from gitignored local.properties.
# Prints, per playlist: raw items paginated, post-filter count (deleted/private/no-thumb
# removed — same rule as VideoMapper), and the API's pageInfo.totalResults.
set -euo pipefail

KEY="$(grep '^YOUTUBE_API_KEY=' local.properties | cut -d= -f2-)"
[ -n "$KEY" ] || { echo "no key"; exit 1; }

# id|title|expected
PLAYLISTS=(
  "PLfxolKs8oDR66iswGEXMQz10w1seo8O80|괜찮Knee TV|85"
  "PLfxolKs8oDR7o8U1FaBEa60UNFGwEsopE|척척박사 TV|76"
  "PLfxolKs8oDR4vTPuHa9Bp5CV22IHCC7H7|손봐줘 TV|56"
  "PLfxolKs8oDR6LzV8jvySqtmcNfaBaA9pN|Hip한 NOW|35"
  "PLfxolKs8oDR6a7TCf6Ok1ilR8I5ZqkdC1|어깨의 정석|58"
  "PLfxolKs8oDR4x2gGkTk29RpJ7aysqo0J3|족집게 TV|41"
)

printf "%-14s %6s %6s %8s %5s\n" "part" "raw" "filt" "apiTotal" "exp"
for row in "${PLAYLISTS[@]}"; do
  IFS='|' read -r pid title exp <<< "$row"
  token=""
  raw=0; filt=0; apitotal=""
  while : ; do
    url="https://www.googleapis.com/youtube/v3/playlistItems?part=snippet,contentDetails&maxResults=50&playlistId=${pid}&key=${KEY}"
    [ -n "$token" ] && url="${url}&pageToken=${token}"
    resp="$(curl -s "$url")"
    err="$(echo "$resp" | jq -r '.error.message // empty')"
    [ -n "$err" ] && { echo "$title: API ERROR: $err"; break; }
    [ -z "$apitotal" ] && apitotal="$(echo "$resp" | jq -r '.pageInfo.totalResults')"
    n="$(echo "$resp" | jq '.items | length')"
    raw=$((raw + n))
    # filter: title not Deleted/Private AND has at least one thumbnail url
    f="$(echo "$resp" | jq '[.items[] | select((.snippet.title // "") as $t | ($t != "Deleted video") and ($t != "Private video") and ($t != "")) | select((.snippet.thumbnails // {} | to_entries | map(.value.url) | map(select(. != null and . != "")) | length) > 0)] | length')"
    filt=$((filt + f))
    token="$(echo "$resp" | jq -r '.nextPageToken // empty')"
    [ -z "$token" ] && break
  done
  printf "%-14s %6s %6s %8s %5s\n" "$title" "$raw" "$filt" "$apitotal" "$exp"
done
