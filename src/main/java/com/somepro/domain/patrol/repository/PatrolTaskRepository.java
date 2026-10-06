package com.somepro.domain.patrol.repository;

import com.somepro.domain.patrol.model.PatrolTask;
import com.somepro.domain.patrol.model.PatrolTaskQuery;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 巡护任务聚合的仓储端口：由领域层定义，基础设施层实现（端口-适配器）。
 */
public interface PatrolTaskRepository {

    /**
     * 派发落库：taskNo 由实现侧按 PT-YYYY-NNNN 生成（并发撞号自动重取，唯一索引兜底）。
     * 「同点同日占位」校验与落库在实现侧同一事务 + 同名锁内完成，并发派发只会成功一条。
     */
    Mono<PatrolTask> create(PatrolTask task);

    /**
     * 改派落库：编号不改。改派同样要在锁内重查「同点同日占位」（排除自己），
     * 避免两个编辑请求互相踩坑。
     */
    Mono<PatrolTask> update(PatrolTask task);

    /**
     * 状态流转落库（取消用）：只改状态，不做同点同日占位检查 ——
     * 取消不涉及往哪个坑派单，且取消后坑位本来就该让出来。
     */
    Mono<PatrolTask> updateStatus(PatrolTask task);

    Mono<PatrolTask> findById(Long id);

    /** 条件分页：条件全空时返回整份名单（默认不带已取消任务）。 */
    Mono<PageResult<PatrolTask>> page(int pageNum, int pageSize, PatrolTaskQuery query);

    /**
     * 某点某天还挂着几条没走完的任务（PENDING / IN_PROGRESS 占位）。
     * @param excludeId 编辑任务重查占位时排除自身；新建传 null
     */
    Mono<Long> countOpenSlots(Long siteId, java.time.LocalDate plannedDate, Long excludeId);

    /** 撤单：逻辑删除（@TableLogic 置 del_flag=1），账仍留在表里，名单里不再翻得到。 */
    Mono<Void> softDelete(Long id);
}
