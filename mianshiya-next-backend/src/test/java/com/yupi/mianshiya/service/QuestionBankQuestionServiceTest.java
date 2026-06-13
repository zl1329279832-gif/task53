package com.yupi.mianshiya.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yupi.mianshiya.mapper.QuestionBankQuestionMapper;
import com.yupi.mianshiya.model.entity.QuestionBankQuestion;
import com.yupi.mianshiya.service.impl.QuestionBankQuestionServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * 题库题目关联服务回归测试
 */
@ExtendWith(MockitoExtension.class)
class QuestionBankQuestionServiceTest {

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                QuestionBankQuestion.class
        );
    }

    @Mock
    private UserService userService;

    @Mock
    private QuestionService questionService;

    @Mock
    private QuestionBankService questionBankService;

    @Mock
    private QuestionBankQuestionMapper questionBankQuestionMapper;

    @Spy
    @InjectMocks
    private QuestionBankQuestionServiceImpl questionBankQuestionService;

    /**
     * Bug 7 回归：批量从题库移除题目，当题目不在题库中时不应报错
     */
    @Test
    void testBatchRemoveQuestionsFromBank_questionNotInBank_succeeds() {
        List<Long> questionIdList = Arrays.asList(1L, 2L);
        long questionBankId = 100L;

        // 模拟题目不在题库中（remove 返回 false）
        doReturn(false).when(questionBankQuestionService).remove(any(LambdaQueryWrapper.class));

        // 不应抛出异常
        assertDoesNotThrow(() ->
                questionBankQuestionService.batchRemoveQuestionsFromBank(questionIdList, questionBankId));
    }

    /**
     * Bug 7 回归：正常移除题目应成功
     */
    @Test
    void testBatchRemoveQuestionsFromBank_normalRemoval_succeeds() {
        List<Long> questionIdList = Arrays.asList(1L, 2L);
        long questionBankId = 100L;

        // 模拟正常移除
        doReturn(true).when(questionBankQuestionService).remove(any(LambdaQueryWrapper.class));

        assertDoesNotThrow(() ->
                questionBankQuestionService.batchRemoveQuestionsFromBank(questionIdList, questionBankId));

        // 验证每个题目都执行了移除
        verify(questionBankQuestionService, org.mockito.Mockito.times(2)).remove(any(LambdaQueryWrapper.class));
    }

    /**
     * Bug 7 回归：混合场景 - 部分题目在题库中，部分不在
     */
    @Test
    void testBatchRemoveQuestionsFromBank_mixedResults_succeeds() {
        List<Long> questionIdList = Arrays.asList(1L, 2L, 3L);
        long questionBankId = 100L;

        // 第一次返回 true（题目在库中），第二次返回 false（不在库中），第三次返回 true
        doReturn(true).doReturn(false).doReturn(true)
                .when(questionBankQuestionService).remove(any(LambdaQueryWrapper.class));

        // 不应抛出异常
        assertDoesNotThrow(() ->
                questionBankQuestionService.batchRemoveQuestionsFromBank(questionIdList, questionBankId));
    }
}
