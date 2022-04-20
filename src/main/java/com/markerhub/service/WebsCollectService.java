package com.markerhub.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.search.model.CollectWebsiteDocument;

/**
 * @author mingchiuli
 * @create 2022-04-20 11:02 AM
 */
public interface WebsCollectService {

    String getJWT();

    void addWebsite(CollectWebsiteDocument document);

    void modifyWebsite(CollectWebsiteDocument document);

    Page<CollectWebsiteDocument> searchWebsiteAuth(Integer currentPage, String keyword);

    Page<CollectWebsiteDocument> searchRecent(Integer currentPage);


    Page<CollectWebsiteDocument> searchWebsite(Integer currentPage, String keyword);
}
