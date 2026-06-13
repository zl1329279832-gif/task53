package com.yupi.mianshiya.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.mianshiya.mapper.InterviewSprintPlanTaskMapper;
import com.yupi.mianshiya.model.entity.InterviewSprintPlanTask;
import com.yupi.mianshiya.model.entity.Post;
import com.yupi.mianshiya.model.entity.Question;
import com.yupi.mianshiya.model.vo.PostVO;
import com.yupi.mianshiya.model.vo.QuestionVO;
import com.yupi.mianshiya.model.vo.sprintplan.InterviewSprintPlanTaskVO;
import com.yupi.mianshiya.service.InterviewSprintPlanTaskService;
import com.yupi.mianshiya.service.PostService;
import com.yupi.mianshiya.service.QuestionService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 面试冲刺计划每日任务服务实现
 */
@Service
public class InterviewSprintPlanTaskServiceImpl
        extends ServiceImpl<InterviewSprintPlanTaskMapper, InterviewSprintPlanTask>
        implements InterviewSprintPlanTaskService {

    @Resource
    private QuestionService questionService;

    @Resource
    @Lazy
    private PostService postService;

    @Override
    public void batchCreateTasks(List<InterviewSprintPlanTask> tasks) {
        if (CollUtil.isEmpty(tasks)) {
            return;
        }
        this.saveBatch(tasks, 500);
    }

    @Override
    public List<InterviewSprintPlanTaskVO> listTasksByPlanId(Long planId, Long userId) {
        LambdaQueryWrapper<InterviewSprintPlanTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(InterviewSprintPlanTask::getPlanId, planId)
                .eq(InterviewSprintPlanTask::getUserId, userId)
                .orderByAsc(InterviewSprintPlanTask::getDayNumber);
        List<InterviewSprintPlanTask> tasks = this.list(queryWrapper);
        if (CollUtil.isEmpty(tasks)) {
            return Collections.emptyList();
        }
        return tasks.stream()
                .map(this::enrichTaskVO)
                .collect(Collectors.toList());
    }

    @Override
    public InterviewSprintPlanTaskVO enrichTaskVO(InterviewSprintPlanTask task) {
        InterviewSprintPlanTaskVO vo = new InterviewSprintPlanTaskVO();
        BeanUtils.copyProperties(task, vo);

        // 解析题目 id JSON 数组并展开为 QuestionVO
        if (StrUtil.isNotBlank(task.getQuestionIds())) {
            List<Long> qIds = JSONUtil.toList(JSONUtil.parseArray(task.getQuestionIds()), Long.class);
            if (CollUtil.isNotEmpty(qIds)) {
                List<Question> questions = questionService.listByIds(qIds);
                Map<Long, Question> qMap = questions.stream()
                        .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));
                vo.setQuestions(qIds.stream()
                        .filter(qMap::containsKey)
                        .map(id -> QuestionVO.objToVo(qMap.get(id)))
                        .collect(Collectors.toList()));
            } else {
                vo.setQuestions(Collections.emptyList());
            }
        } else {
            vo.setQuestions(Collections.emptyList());
        }

        // 解析帖子 id JSON 数组并展开为 PostVO
        if (StrUtil.isNotBlank(task.getPostIds())) {
            List<Long> pIds = JSONUtil.toList(JSONUtil.parseArray(task.getPostIds()), Long.class);
            if (CollUtil.isNotEmpty(pIds)) {
                List<Post> posts = postService.listByIds(pIds);
                Map<Long, Post> pMap = posts.stream()
                        .collect(Collectors.toMap(Post::getId, p -> p, (a, b) -> a));
                vo.setPosts(pIds.stream()
                        .filter(pMap::containsKey)
                        .map(id -> PostVO.objToVo(pMap.get(id)))
                        .collect(Collectors.toList()));
            } else {
                vo.setPosts(Collections.emptyList());
            }
        } else {
            vo.setPosts(Collections.emptyList());
        }

        return vo;
    }
}
