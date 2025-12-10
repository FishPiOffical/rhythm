BASE="http://127.0.0.1:8080"
API_KEY=""

BOOK_ID=""
VOL_ID=""
CHAPTER_ID=""
BM_ID=""
CMT_ID=""

curl -sS -X POST "$BASE/yuhu/book?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"title":"测试书","intro":"这是简介","authorProfileId":"author-profile-id","coverURL":"https://example.com/cover.jpg"}'
curl -sS "$BASE/yuhu/books?q=测试&sort=&page=1&size=20"
curl -sS "$BASE/yuhu/book/$BOOK_ID"

curl -sS -X POST "$BASE/yuhu/book/$BOOK_ID/volume?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"title":"卷一","intro":"卷一简介"}'
curl -sS "$BASE/yuhu/book/$BOOK_ID/volumes"

curl -sS -X POST "$BASE/yuhu/book/$BOOK_ID/chapter?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"volumeId":"'$VOL_ID'","title":"第一章","contentMD":"内容 Markdown","isPaid":false}'
curl -sS -X PUT "$BASE/yuhu/chapter/$CHAPTER_ID/draft?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"title":"第一章（修订）","contentMD":"更新后的 Markdown 内容"}'
curl -sS -X PUT "$BASE/yuhu/chapter/$CHAPTER_ID/publish?apiKey=$API_KEY"
curl -sS -X PUT "$BASE/yuhu/chapter/$CHAPTER_ID/approve?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"note":"LGTM"}'
curl -sS -X PUT "$BASE/yuhu/chapter/$CHAPTER_ID/reject?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"note":"需要修订"}'
curl -sS -X PUT "$BASE/yuhu/chapter/$CHAPTER_ID/freeze?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"reason":"质量问题待修"}'
curl -sS -X PUT "$BASE/yuhu/chapter/$CHAPTER_ID/ban?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"reason":"违规内容"}'

curl -sS "$BASE/yuhu/prefs?apiKey=$API_KEY"
curl -sS -X POST "$BASE/yuhu/prefs?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"theme":"dark","fontSize":18,"pageWidth":800}'

curl -sS "$BASE/yuhu/progress/$BOOK_ID?apiKey=$API_KEY"
curl -sS -X POST "$BASE/yuhu/progress/$BOOK_ID?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"chapterId":"'$CHAPTER_ID'","percent":66}'

curl -sS "$BASE/yuhu/bookmarks?apiKey=$API_KEY&bookId=$BOOK_ID"
curl -sS -X POST "$BASE/yuhu/bookmarks?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"bookId":"'$BOOK_ID'","chapterId":"'$CHAPTER_ID'","paragraphId":"p-1","offset":12}'
curl -sS -X DELETE "$BASE/yuhu/bookmarks/$BM_ID?apiKey=$API_KEY"

curl -sS -X POST "$BASE/yuhu/comment?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"bookId":"'$BOOK_ID'","chapterId":"'$CHAPTER_ID'","paragraphId":"p-1","content":"这章不错"}'
curl -sS "$BASE/yuhu/comments?chapterId=$CHAPTER_ID&page=1&size=20"
curl -sS -X DELETE "$BASE/yuhu/comment/$CMT_ID?apiKey=$API_KEY"

curl -sS -X POST "$BASE/yuhu/tag?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"name":"奇幻","aliasEN":"fantasy","desc":"奇幻题材"}'
curl -sS "$BASE/yuhu/tags"
curl -sS -X POST "$BASE/yuhu/book/$BOOK_ID/tags?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"tags":["fantasy"]}'

curl -sS -X POST "$BASE/yuhu/subscribe/$BOOK_ID?apiKey=$API_KEY"
curl -sS -X DELETE "$BASE/yuhu/subscribe/$BOOK_ID?apiKey=$API_KEY"
curl -sS "$BASE/yuhu/subscriptions?apiKey=$API_KEY"

curl -sS -X POST "$BASE/yuhu/vote?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"type":"tip","targetType":"book","targetId":"'$BOOK_ID'","value":128}'
curl -sS "$BASE/yuhu/vote/stats?bookId=$BOOK_ID"
curl -sS "$BASE/yuhu/subscription/stats?bookId=$BOOK_ID"

curl -sS "$BASE/yuhu/search?q=测试"

curl -sS -X POST "$BASE/yuhu/profile/display?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"displayOverrideEnabled":true}'

# Author queries
curl -sS "$BASE/yuhu/author/author-profile-id"
curl -sS "$BASE/yuhu/author/byBook/$BOOK_ID"
curl -sS "$BASE/yuhu/author/author-profile-id/stats"
curl -sS "$BASE/yuhu/author/author-profile-id/me?apiKey=$API_KEY"
curl -sS "$BASE/yuhu/author/author-profile-id/books?page=1&size=20"

# Admin comment management
curl -sS "$BASE/yuhu/admin/comments?bookId=$BOOK_ID&chapterId=$CHAPTER_ID&profileId=author-profile-id&status=&q=&page=1&size=20&apiKey=$API_KEY"
curl -sS -X PUT "$BASE/yuhu/comment/$COMMENT_ID?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"content":"新内容","status":"normal"}'

# Author comment management
curl -sS "$BASE/yuhu/admin/comments?bookId=$BOOK_ID&apiKey=$API_KEY"
curl -sS -X PUT "$BASE/yuhu/comment/$COMMENT_ID?apiKey=$API_KEY" -H 'Content-Type: application/json' -d '{"content":"作者修订"}'
