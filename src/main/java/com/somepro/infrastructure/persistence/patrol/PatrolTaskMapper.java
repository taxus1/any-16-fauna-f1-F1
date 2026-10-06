package com.somepro.infrastructure.persistence.patrol;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.patrol.po.PatrolTaskPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 巡护任务的 MyBatis-Plus Mapper（基础设施层）。
 *
 * 阻塞（JDBC）API，只能在 boundedElastic 线程上调用。
 */
@Mapper
public interface PatrolTaskMapper extends BaseMapper<PatrolTaskPO> {

    /**
     * 查指定号段内已用的最大序号（编号生成用）。
     * 自定义 @Select 不拼 del_flag 条件：已撤任务占用的编号也不复用。
     */
    @Select("SELECT MAX(CAST(SUBSTRING(task_no, #{seqStart}) AS UNSIGNED)) "
            + "FROM t_patrol_task WHERE task_no LIKE CONCAT(#{prefix}, '%')")
    Long selectMaxSeq(@Param("prefix") String prefix, @Param("seqStart") int seqStart);

    /**
     * MySQL 命名锁：同点同日派单的串行闸。
     * 必须和后续查重 / 落库跑在【同一个连接】上（@Transactional 保证）：
     * 返回 1 拿锁成功；0 等待超时（别人正占着）；NULL 出错。
     */
    @Select("SELECT GET_LOCK(#{name}, #{timeout})")
    Integer getLock(@Param("name") String name, @Param("timeout") int timeout);

    /** 释放命名锁：返回 1 释放成功；0 本连接没持有；NULL 锁不存在。 */
    @Select("SELECT RELEASE_LOCK(#{name})")
    Integer releaseLock(@Param("name") String name);
}
