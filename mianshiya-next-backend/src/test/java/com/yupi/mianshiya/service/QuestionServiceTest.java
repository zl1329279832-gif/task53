package com.yupi.mianshiya.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.mianshiya.esdao.QuestionEsDao;
import com.yupi.mianshiya.manager.AiManager;
import com.yupi.mianshiya.model.dto.question.QuestionEsDTO;
import com.yupi.mianshiya.model.dto.question.QuestionQueryRequest;
import com.yupi.mianshiya.model.entity.Question;
import com.yupi.mianshiya.model.entity.QuestionBankQuestion;
import com.yupi.mianshiya.service.impl.QuestionServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 题目服务回归测试
 */
@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @BeforeAll
    static void initLambdaCache() {
        // 初始化 MyBatis-Plus Lambda 缓存，避免在无 Spring 环境下 LambdaQueryWrapper 报错
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                QuestionBankQuestion.class
        );
    }

    @Mock
    private UserService userService;

    @Mock
    private QuestionBankQuestionService questionBankQuestionService;

    @Mock
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Mock
    private QuestionEsDao questionEsDao;

    @Mock
    private AiManager aiManager;

    @Spy
    @InjectMocks
    private QuestionServiceImpl questionService;

    /**
     * Bug 3 回归：批量删除题目时，如果题目没有题库关联，不应报错
     */
    @Test
    void testBatchDeleteQuestions_withNoAssociations_succeeds() {
        List<Long> questionIdList = Arrays.asList(1L, 2L);
        // 模拟逻辑删除成功
        doReturn(true).when(questionService).removeById(1L);
        doReturn(true).when(questionService).removeById(2L);
        // 模拟没有题库关联（remove 返回 false）
        when(questionBankQuestionService.remove(any(LambdaQueryWrapper.class))).thenReturn(false);

        // 不应抛出异常
        assertDoesNotThrow(() -> questionService.batchDeleteQuestions(questionIdList));
    }

    /**
     * Bug 4 回归：批量删除题目后，应同步 ES
     */
    @Test
    void testBatchDeleteQuestions_syncsToEs() {
        List<Long> questionIdList = Arrays.asList(1L, 2L);
        doReturn(true).when(questionService).removeById(anyLong());
        when(questionBankQuestionService.remove(any(LambdaQueryWrapper.class))).thenReturn(true);

        questionService.batchDeleteQuestions(questionIdList);

        // 验证 ES 同步被调用，且 isDelete=1
        verify(questionEsDao).saveAll(argThat(list -> {
            List<QuestionEsDTO> dtoList = (List<QuestionEsDTO>) list;
            return dtoList.size() == 2
                    && dtoList.stream().allMatch(dto -> dto.getIsDelete() == 1);
        }));
    }

    /**
     * Bug 1 回归：searchFromEs 按 questionBankId 搜索时，应通过关联表查找题目 id
     */
    @Test
    void testSearchFromEs_withQuestionBankId_queriesJoinTable() {
        QuestionQueryRequest request = new QuestionQueryRequest();
        request.setQuestionBankId(100L);
        request.setCurrent(1);
        request.setPageSize(10);

        // 模拟关联表中有题目 1 和 2
        QuestionBankQuestion qbq1 = new QuestionBankQuestion();
        qbq1.setQuestionId(1L);
        QuestionBankQuestion qbq2 = new QuestionBankQuestion();
        qbq2.setQuestionId(2L);
        when(questionBankQuestionService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(qbq1, qbq2));

        // 模拟 ES 返回空结果
        SearchHits<QuestionEsDTO> emptyHits = mock(SearchHits.class);
        when(emptyHits.getTotalHits()).thenReturn(0L);
        when(emptyHits.hasSearchHits()).thenReturn(false);
        when(elasticsearchRestTemplate.search(any(NativeSearchQuery.class), eq(QuestionEsDTO.class))).thenReturn(emptyHits);

        Page<Question> result = questionService.searchFromEs(request);

        // 验证查询了关联表
        verify(questionBankQuestionService).list(any(LambdaQueryWrapper.class));
        // 验证调用了 ES
        verify(elasticsearchRestTemplate).search(any(NativeSearchQuery.class), eq(QuestionEsDTO.class));
        assertNotNull(result);
    }

    /**
     * Bug 1 回归：searchFromEs 当题库为空时，直接返回空结果，不查 ES
     */
    @Test
    void testSearchFromEs_withEmptyBank_returnsEmpty() {
        QuestionQueryRequest request = new QuestionQueryRequest();
        request.setQuestionBankId(100L);
        request.setCurrent(1);
        request.setPageSize(10);

        // 模拟关联表为空
        when(questionBankQuestionService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        Page<Question> result = questionService.searchFromEs(request);

        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        // 不应查询 ES
        verifyNoInteractions(elasticsearchRestTemplate);
    }

    /**
     * Bug 5 回归：删除题目后应清理关联数据和 ES
     */
    @Test
    void testCleanupAfterQuestionDelete() {
        Long questionId = 1L;

        questionService.cleanupAfterQuestionDelete(questionId);

        // 验证清理了关联
        verify(questionBankQuestionService).remove(any(LambdaQueryWrapper.class));
        // 验证删除了 ES 数据
        verify(questionEsDao).deleteById(questionId);
    }

    /**
     * ES 同步异常不应影响主流程
     */
    @Test
    void testSyncQuestionToEs_exceptionSwallowed() {
        Question question = new Question();
        question.setId(1L);
        question.setTitle("test");
        question.setTags("[]");

        when(questionEsDao.save(any())).thenThrow(new RuntimeException("ES connection refused"));

        // 不应抛出异常
        assertDoesNotThrow(() -> questionService.syncQuestionToEs(question));
    }

    /**
     * ES 降级查询：searchFromEs 失败时，controller 应降级到 MySQL
     * 这里测试 service 层 searchFromEs 在 ES 异常时确实抛出异常（供 controller catch）
     */
    @Test
    void testSearchFromEs_esUnavailable_throwsException() {
        QuestionQueryRequest request = new QuestionQueryRequest();
        request.setCurrent(1);
        request.setPageSize(10);

        when(elasticsearchRestTemplate.search(any(NativeSearchQuery.class), eq(QuestionEsDTO.class)))
                .thenThrow(new RuntimeException("ES unavailable"));

        assertThrows(RuntimeException.class,
                () -> questionService.searchFromEs(request));
    }
}
