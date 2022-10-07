package com.markerhub.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.vo.WebsitePostDocumentVo;
import com.markerhub.search.model.WebsitePostDocument;

/**
 * @author mingchiuli
 * @create 2022-04-20 11:02 AM
 */
public interface WebsCollectService {

    String getJWT();

    void addWebsite(WebsitePostDocument document);

    void modifyWebsite(WebsitePostDocument document);

    Page<WebsitePostDocumentVo> searchWebsiteAuth(Integer currentPage, String keyword);

    Page<WebsitePostDocument> searchRecent(Integer currentPage);

    Page<WebsitePostDocumentVo> searchWebsite(Integer currentPage, String keyword);
}
