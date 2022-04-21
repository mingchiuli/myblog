package com.markerhub.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.vo.WebsCollectDocumentVo;
import com.markerhub.search.model.WebsCollectDocument;

/**
 * @author mingchiuli
 * @create 2022-04-20 11:02 AM
 */
public interface WebsCollectService {

    String getJWT();

    void addWebsite(WebsCollectDocument document);

    void modifyWebsite(WebsCollectDocument document);

    Page<WebsCollectDocumentVo> searchWebsiteAuth(Integer currentPage, String keyword);

    Page<WebsCollectDocument> searchRecent(Integer currentPage);

    Page<WebsCollectDocumentVo> searchWebsite(Integer currentPage, String keyword);
}
