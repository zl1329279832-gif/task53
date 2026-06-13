-- 面试冲刺计划表
CREATE TABLE IF NOT EXISTS `interview_sprint_plan`
(
    `id`             BIGINT       NOT NULL COMMENT 'id（雪花算法）',
    `userId`         BIGINT       NOT NULL COMMENT '用户 id',
    `planName`       VARCHAR(128) NOT NULL DEFAULT '' COMMENT '计划名称',
    `duration`       INT          NOT NULL COMMENT '计划天数：7 或 14',
    `sourceType`     INT          NOT NULL COMMENT '来源类型：0-题库, 1-标签, 2-收藏帖子, 3-模拟面试记录',
    `sourceId`       BIGINT       DEFAULT NULL COMMENT '来源 id（题库 id / 模拟面试 id，标签和收藏为 null）',
    `startDate`      DATE         NOT NULL COMMENT '计划开始日期',
    `endDate`        DATE         NOT NULL COMMENT '计划结束日期',
    `totalQuestions` INT          NOT NULL DEFAULT 0 COMMENT '计划总题目数',
    `completedCount` INT          NOT NULL DEFAULT 0 COMMENT '已完成天数',
    `status`         INT          NOT NULL DEFAULT 0 COMMENT '状态：0-进行中, 1-已完成, 2-已废弃',
    `createTime`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_userId` (`userId`),
    KEY `idx_userId_status` (`userId`, `status`, `isDelete`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='面试冲刺计划';

-- 面试冲刺计划每日任务表
CREATE TABLE IF NOT EXISTS `interview_sprint_plan_task`
(
    `id`                BIGINT       NOT NULL COMMENT 'id（雪花算法）',
    `planId`            BIGINT       NOT NULL COMMENT '所属计划 id',
    `userId`            BIGINT       NOT NULL COMMENT '用户 id（冗余，便于权限校验）',
    `dayNumber`         INT          NOT NULL COMMENT '第几天，从 1 开始',
    `taskDate`          DATE         NOT NULL COMMENT '任务日期',
    `questionIds`       TEXT         DEFAULT NULL COMMENT '题目 id 列表，JSON 数组',
    `postIds`           TEXT         DEFAULT NULL COMMENT '推荐帖子 id 列表，JSON 数组',
    `mockInterviewGoal` VARCHAR(512) DEFAULT '' COMMENT '模拟面试目标描述',
    `studyNotes`        TEXT         DEFAULT NULL COMMENT '学习要点',
    `status`            INT          NOT NULL DEFAULT 0 COMMENT '完成状态：0-未完成, 1-已完成',
    `completedTime`     DATETIME     DEFAULT NULL COMMENT '完成时间',
    `createTime`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`          TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_planId` (`planId`),
    KEY `idx_userId_taskDate` (`userId`, `taskDate`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='面试冲刺计划每日任务';
