package com.markerhub.search.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author mingchiuli
 * @create 2022-01-29 2:57 PM
 */
@Data
@Document(indexName = "websitecollect")
public class WebsCollectDocument implements Serializable {
    @Id
    private String id;
    @Field(type = FieldType.Keyword)
    private Integer status;
    @Field(type = FieldType.Text, searchAnalyzer = "ik_smart", analyzer = "ik_max_word")
    @NotBlank(message = "标题不能为空")
    private String title;
    @Field(type = FieldType.Text, searchAnalyzer = "ik_smart", analyzer = "ik_max_word")
    @NotBlank(message = "描述不能为空")
    private String description;
    @Field(type = FieldType.Text)
    @NotBlank(message = "链接不能为空")
    private String link;

    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Field(type = FieldType.Date, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime created;


}
