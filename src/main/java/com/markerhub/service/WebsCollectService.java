package com.markerhub.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.search.model.WebsCollectDocument;

/**
 * @author mingchiuli
 * @create 2022-04-20 11:02 AM
 */
public interface WebsCollectService {

    String getJWT();

    void addWebsite(WebsCollectDocument document);

    void modifyWebsite(WebsCollectDocument document);

    Page<WebsCollectDocument> searchWebsiteAuth(Integer currentPage, String keyword);

    Page<WebsCollectDocument> searchRecent(Integer currentPage);


    Page<WebsCollectDocument> searchWebsite(Integer currentPage, String keyword);
}
