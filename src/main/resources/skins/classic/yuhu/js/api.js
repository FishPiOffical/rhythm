function yuhuAPI(url, options) {
  return new Promise((resolve, reject) => {
    fetch(url, options)
      .then((response) => response.json())
      .then((data) => {
        if (data.code == 0) {
          resolve(data);
        } else {
          reject(data);
        }
      })
      .catch((error) => {
        reject(error);
      });
  });
}

// 创建鱼乎
export const createYuhu = async (
  title,
  intro,
  authorProfileId,
  coverURL,
  tags
) => {
  return yuhuAPI("/yuhu/book", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      title: title,
      intro: intro,
      authorProfileId: authorProfileId,
      coverURL: coverURL,
      tags: tags,
    }),
  });
};

// 查询鱼乎列表
export const queryYuhu = async (tag,q,sort,page,size) => {
    const params = new URLSearchParams();
    params.append("tag", tag);
    params.append("q", q);
    params.append("sort", sort);
    params.append("page", page);
    params.append("size", size);
  return yuhuAPI(`/yuhu/books/${params.toString()}`, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
    },
  });
};

// 获取鱼乎详情
export const getYuhu = async (id) => {
  return yuhuAPI(`/yuhu/book/${id}`, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
    },
  });
};