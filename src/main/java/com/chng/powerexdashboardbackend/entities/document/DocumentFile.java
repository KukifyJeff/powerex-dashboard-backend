package com.chng.powerexdashboardbackend.entities.document;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document_files")
public class DocumentFile {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    private String originalName;

    private String storedName;

    private String directory;

    private String filePath;

    private String contentType;

    private Long sizeBytes;

    private String uploadedBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
