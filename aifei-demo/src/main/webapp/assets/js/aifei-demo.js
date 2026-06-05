(function (window) {
  const Aifei = window.Aifei || (window.Aifei = {});

  function buildUrl(url, data) {
    if (!data) {
      return url;
    }

    const query = new URLSearchParams();
    Object.keys(data).forEach(key => {
      const value = data[key];
      if (value !== undefined && value !== null) {
        query.append(key, value);
      }
    });

    const queryString = query.toString();
    if (!queryString) {
      return url;
    }

    return url + (url.indexOf("?") === -1 ? "?" : "&") + queryString;
  }

  function parseJson(response) {
    return response.text().then(text => text ? JSON.parse(text) : null);
  }

  function request(url, options) {
    return fetch(url, options || {})
      .then(response => {
        if (!response.ok) {
          throw new Error("请求失败：" + response.status);
        }
        return parseJson(response);
      })
      .then(json => {
        if (json && json.code !== undefined && Number(json.code) !== 0) {
          throw new Error(json.msg || "操作失败");
        }
        return json ? json.data : null;
      });
  }

  function get(url, data, options) {
    const config = options || {};
    return request(buildUrl(url, data), Object.assign({}, config, {
      method: "GET",
      headers: Object.assign({
        "Accept": "application/json"
      }, config.headers || {})
    }));
  }

  function post(url, data, options) {
    const config = options || {};
    return request(url, Object.assign({}, config, {
      method: "POST",
      headers: Object.assign({
        "Content-Type": "application/json",
        "Accept": "application/json"
      }, config.headers || {}),
      body: JSON.stringify(data || {})
    }));
  }

  Aifei.Request = {
    request: request,
    get: get,
    post: post
  };
})(window);
