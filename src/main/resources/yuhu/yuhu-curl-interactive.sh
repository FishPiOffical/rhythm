#!/usr/bin/env bash
set -euo pipefail

read -r -p "BASE (default http://127.0.0.1:8080): " BASE
BASE=${BASE:-http://127.0.0.1:8080}
read -r -p "API_KEY: " API_KEY

if ! command -v jq >/dev/null 2>&1; then
  echo "jq not found"
  exit 1
fi

json_post() { curl -sS -X POST "$1" -H 'Content-Type: application/json' -d "$2"; }
json_put() { curl -sS -X PUT "$1" -H 'Content-Type: application/json' -d "$2"; }

BOOK_ID=""
VOL_ID=""
CHAPTER_ID=""
BM_ID=""
CMT_ID=""
BOOK_TITLE=""

print_state() {
  echo "BOOK_ID=$BOOK_ID"
  echo "VOL_ID=$VOL_ID"
  echo "CHAPTER_ID=$CHAPTER_ID"
  echo "BM_ID=$BM_ID"
  echo "CMT_ID=$CMT_ID"
}

ask() {
  local prompt default var
  prompt="$1"; default="$2"; var="$3"
  read -r -p "$prompt [$default]: " input
  input=${input:-$default}
  printf -v "$var" '%s' "$input"
}

while true; do
  echo "1) Create book"
  echo "2) List books"
  echo "3) Get book"
  echo "4) Create volume"
  echo "5) List volumes"
  echo "6) Create chapter"
  echo "7) Update chapter draft"
  echo "8) Submit chapter for review"
  echo "8a) Approve chapter"
  echo "8b) Reject chapter"
  echo "9) Freeze chapter"
  echo "10) Ban chapter"
  echo "11) Get prefs"
  echo "12) Set prefs"
  echo "13) Get progress"
  echo "14) Set progress"
  echo "15) List bookmarks"
  echo "16) Add bookmark"
  echo "17) Delete bookmark"
  echo "18) Add comment"
  echo "19) List comments"
  echo "20) Delete comment"
  echo "21) Add tag"
  echo "22) List tags"
  echo "23) Bind tags"
  echo "24) Subscribe book"
  echo "25) Unsubscribe book"
  echo "26) List subscriptions"
  echo "27) Vote tip"
  echo "28) Vote stats"
  echo "29) Search"
  echo "31) Get author by profileId"
  echo "32) Get author by bookId"
  echo "33) Get author stats"
  echo "34) Get my author profile"
  echo "35) List author books"
  echo "30) Profile display"
  echo "99) Print state"
  echo "0) Exit"
  read -r -p "Select: " sel
  case "$sel" in
    1)
      ask "Book title" "$BOOK_TITLE" BOOK_TITLE
      ask "Book intro" "这是简介" BOOK_INTRO
      ask "Author profileId" "author-profile-id" AUTHOR_PROFILE_ID
      ask "Cover URL" "https://example.com/cover.jpg" COVER_URL
      resp=$(json_post "$BASE/yuhu/book?apiKey=$API_KEY" "{\"title\":\"$BOOK_TITLE\",\"intro\":\"$BOOK_INTRO\",\"authorProfileId\":\"$AUTHOR_PROFILE_ID\",\"coverURL\":\"$COVER_URL\"}")
      echo "$resp"
      BOOK_ID=$(echo "$resp" | jq -r '.data.oId // .data.id // .oId // .id')
      ;;
    2)
      ask "Query" "$BOOK_TITLE" q
      curl -sS "$BASE/yuhu/books?q=$q&sort=&page=1&size=20"
      ;;
    3)
      ask "Book ID" "$BOOK_ID" BOOK_ID
      curl -sS "$BASE/yuhu/book/$BOOK_ID"
      ;;
    4)
      ask "Book ID" "$BOOK_ID" BOOK_ID
      ask "Volume title" "卷一" VOL_TITLE
      ask "Volume intro" "卷一简介" VOL_INTRO
      resp=$(json_post "$BASE/yuhu/book/$BOOK_ID/volume?apiKey=$API_KEY" "{\"title\":\"$VOL_TITLE\",\"intro\":\"$VOL_INTRO\"}")
      echo "$resp"
      VOL_ID=$(echo "$resp" | jq -r '.data.oId // .data.yuhuVolumeIndex // .oId // .yuhuVolumeIndex')
      ;;
    5)
      ask "Book ID" "$BOOK_ID" BOOK_ID
      curl -sS "$BASE/yuhu/book/$BOOK_ID/volumes"
      ;;
    6)
      ask "Book ID" "$BOOK_ID" BOOK_ID
      ask "Volume ID" "$VOL_ID" VOL_ID
      ask "Chapter title" "第一章" CHAPTER_TITLE
      ask "Chapter content MD" "内容 Markdown" CHAPTER_MD
      ask "Is paid (true/false)" "false" IS_PAID
      resp=$(json_post "$BASE/yuhu/book/$BOOK_ID/chapter?apiKey=$API_KEY" "{\"volumeId\":\"$VOL_ID\",\"title\":\"$CHAPTER_TITLE\",\"contentMD\":\"$CHAPTER_MD\",\"isPaid\":$IS_PAID}")
      echo "$resp"
      CHAPTER_ID=$(echo "$resp" | jq -r '.data.oId // .oId')
      ;;
    7)
      ask "Chapter ID" "$CHAPTER_ID" CHAPTER_ID
      ask "New title" "" DRAFT_TITLE
      ask "New content MD" "" DRAFT_MD
      payload=$(jq -n --arg t "$DRAFT_TITLE" --arg c "$DRAFT_MD" '{title:$t,contentMD:$c}')
      json_put "$BASE/yuhu/chapter/$CHAPTER_ID/draft?apiKey=$API_KEY" "$payload"
      ;;
    8)
      ask "Chapter ID" "$CHAPTER_ID" CHAPTER_ID
      curl -sS -X PUT "$BASE/yuhu/chapter/$CHAPTER_ID/publish?apiKey=$API_KEY"
      ;;
    8a)
      ask "Chapter ID" "$CHAPTER_ID" CHAPTER_ID
      ask "Note" "" note
      json_put "$BASE/yuhu/chapter/$CHAPTER_ID/approve?apiKey=$API_KEY" "{\"note\":\"$note\"}"
      ;;
    8b)
      ask "Chapter ID" "$CHAPTER_ID" CHAPTER_ID
      ask "Note" "" note
      json_put "$BASE/yuhu/chapter/$CHAPTER_ID/reject?apiKey=$API_KEY" "{\"note\":\"$note\"}"
      ;;
    9)
      ask "Chapter ID" "$CHAPTER_ID" CHAPTER_ID
      ask "Reason" "质量问题待修" reason
      json_put "$BASE/yuhu/chapter/$CHAPTER_ID/freeze?apiKey=$API_KEY" "{\"reason\":\"$reason\"}"
      ;;
    10)
      ask "Chapter ID" "$CHAPTER_ID" CHAPTER_ID
      ask "Reason" "违规内容" reason
      json_put "$BASE/yuhu/chapter/$CHAPTER_ID/ban?apiKey=$API_KEY" "{\"reason\":\"$reason\"}"
      ;;
    11)
      curl -sS "$BASE/yuhu/prefs?apiKey=$API_KEY"
      ;;
    12)
      ask "Theme" "dark" theme
      ask "Font size" "18" font
      ask "Page width" "800" width
      json_post "$BASE/yuhu/prefs?apiKey=$API_KEY" "{\"theme\":\"$theme\",\"fontSize\":$font,\"pageWidth\":$width}"
      ;;
    13)
      ask "Book ID" "$BOOK_ID" BOOK_ID
      curl -sS "$BASE/yuhu/progress/$BOOK_ID?apiKey=$API_KEY"
      ;;
    14)
      ask "Book ID" "$BOOK_ID" BOOK_ID
      ask "Chapter ID" "$CHAPTER_ID" CHAPTER_ID
      ask "Percent" "66" percent
      json_post "$BASE/yuhu/progress/$BOOK_ID?apiKey=$API_KEY" "{\"chapterId\":\"$CHAPTER_ID\",\"percent\":$percent}"
      ;;
    15)
      ask "Book ID" "$BOOK_ID" BOOK_ID
      curl -sS "$BASE/yuhu/bookmarks?apiKey=$API_KEY&bookId=$BOOK_ID"
      ;;
    16)
      ask "Book ID" "$BOOK_ID" BOOK_ID
      ask "Chapter ID" "$CHAPTER_ID" CHAPTER_ID
      ask "Paragraph ID" "p-1" pid
      ask "Offset" "12" offset
      resp=$(json_post "$BASE/yuhu/bookmarks?apiKey=$API_KEY" "{\"bookId\":\"$BOOK_ID\",\"chapterId\":\"$CHAPTER_ID\",\"paragraphId\":\"$pid\",\"offset\":$offset}")
      echo "$resp"
      BM_ID=$(echo "$resp" | jq -r '.data.oId // .oId')
      ;;
    17)
      ask "Bookmark ID" "$BM_ID" BM_ID
      curl -sS -X DELETE "$BASE/yuhu/bookmarks/$BM_ID?apiKey=$API_KEY"
      ;;
    18)
      ask "Book ID" "$BOOK_ID" BOOK_ID
      ask "Chapter ID" "$CHAPTER_ID" CHAPTER_ID
      ask "Content" "这章不错" content
      resp=$(json_post "$BASE/yuhu/comment?apiKey=$API_KEY" "{\"bookId\":\"$BOOK_ID\",\"chapterId\":\"$CHAPTER_ID\",\"paragraphId\":\"p-1\",\"content\":\"$content\"}")
      echo "$resp"
      CMT_ID=$(echo "$resp" | jq -r '.data.oId // .oId')
      ;;
    19)
      ask "Chapter ID" "$CHAPTER_ID" CHAPTER_ID
      curl -sS "$BASE/yuhu/comments?chapterId=$CHAPTER_ID&page=1&size=20"
      ;;
    20)
      ask "Comment ID" "$CMT_ID" CMT_ID
      curl -sS -X DELETE "$BASE/yuhu/comment/$CMT_ID?apiKey=$API_KEY"
      ;;
    21)
      ask "Name" "奇幻" name
      ask "Alias EN" "fantasy" alias
      ask "Desc" "奇幻题材" desc
      json_post "$BASE/yuhu/tag?apiKey=$API_KEY" "{\"name\":\"$name\",\"aliasEN\":\"$alias\",\"desc\":\"$desc\"}"
      ;;
    22)
      curl -sS "$BASE/yuhu/tags"
      ;;
    23)
      ask "Book ID" "$BOOK_ID" BOOK_ID
      ask "Tags (comma)" "fantasy" tags
      tjson=$(printf '%s' "$tags" | awk -F',' '{printf "["; for(i=1;i<=NF;i++){printf "%s\"%s\"", (i>1?",":""), $i} printf "]"}')
      json_post "$BASE/yuhu/book/$BOOK_ID/tags?apiKey=$API_KEY" "{\"tags\":$tjson}"
      ;;
    24)
      ask "Book ID" "$BOOK_ID" BOOK_ID
      curl -sS -X POST "$BASE/yuhu/subscribe/$BOOK_ID?apiKey=$API_KEY"
      ;;
    25)
      ask "Book ID" "$BOOK_ID" BOOK_ID
      curl -sS -X DELETE "$BASE/yuhu/subscribe/$BOOK_ID?apiKey=$API_KEY"
      ;;
    26)
      curl -sS "$BASE/yuhu/subscriptions?apiKey=$API_KEY"
      ;;
    27)
      ask "Target type" "book" targetType
      if [ "$targetType" = "chapter" ]; then
        ask "Target ID" "$CHAPTER_ID" targetId
      else
        ask "Target ID" "$BOOK_ID" targetId
      fi
      ask "Value" "128" value
      json_post "$BASE/yuhu/vote?apiKey=$API_KEY" "{\"type\":\"tip\",\"targetType\":\"$targetType\",\"targetId\":\"$targetId\",\"value\":$value}"
      ;;
    28)
      ask "Book ID" "$BOOK_ID" bookId
      curl -sS "$BASE/yuhu/vote/stats?bookId=$bookId"
      ;;
    29)
      ask "Query" "$BOOK_TITLE" q
      curl -sS "$BASE/yuhu/search?q=$q"
      ;;
    30)
      ask "Display override enabled" "true" enabled
      json_post "$BASE/yuhu/profile/display?apiKey=$API_KEY" "{\"displayOverrideEnabled\":$enabled}"
      ;;
    31)
      ask "Author profileId" "author-profile-id" pid
      curl -sS "$BASE/yuhu/author/$pid"
      ;;
    32)
      ask "Book ID" "$BOOK_ID" BOOK_ID
      curl -sS "$BASE/yuhu/author/byBook/$BOOK_ID"
      ;;
    33)
      ask "Author profileId" "author-profile-id" pid
      curl -sS "$BASE/yuhu/author/$pid/stats"
      ;;
    34)
      ask "Author profileId" "author-profile-id" pid
      curl -sS "$BASE/yuhu/author/$pid/me?apiKey=$API_KEY"
      ;;
    35)
      ask "Author profileId" "author-profile-id" pid
      ask "Page" "1" page
      ask "Size" "20" size
      curl -sS "$BASE/yuhu/author/$pid/books?page=$page&size=$size"
      ;;
    99)
      print_state
      ;;
    0)
      exit 0
      ;;
    *)
      echo "Invalid"
      ;;
  esac
done
