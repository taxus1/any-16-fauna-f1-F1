package com.somepro.interfaces.rest.patrol;

import com.somepro.application.patrol.PatrolTaskAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.common.vo.PageVO;
import com.somepro.interfaces.rest.patrol.converter.PatrolTaskVoConverter;
import com.somepro.interfaces.rest.patrol.vo.PatrolTaskCreateRequest;
import com.somepro.interfaces.rest.patrol.vo.PatrolTaskUpdateRequest;
import com.somepro.interfaces.rest.patrol.vo.PatrolTaskVO;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * 巡护任务接口（用户接口层）：只做协议适配与 VO 转换，业务编排交给应用层。
 *
 * 任务默认不物理删除，取消是状态流转：POST /{id}/cancel。
 */
@RestController
@RequestMapping("/api/patrol-tasks")
public class PatrolTaskController {

    private final PatrolTaskAppService taskAppService;

    public PatrolTaskController(PatrolTaskAppService taskAppService) {
        this.taskAppService = taskAppService;
    }

    /** 派发任务：编号服务端生成（PT-YYYY-NNNN），默认待执行。 */
    @PostMapping
    public Mono<Result<PatrolTaskVO>> dispatch(@Valid @RequestBody PatrolTaskCreateRequest req) {
        return taskAppService.dispatch(req.stationId(), req.siteId(), req.patrolType(),
                        req.plannedDate(), req.executor())
                .map(PatrolTaskVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping("/{id}")
    public Mono<Result<PatrolTaskVO>> detail(@PathVariable Long id) {
        return taskAppService.detail(id)
                .map(PatrolTaskVoConverter::toVo)
                .map(Result::ok);
    }

    /** 改派：传啥改啥，只有待执行任务能改。 */
    @PutMapping("/{id}")
    public Mono<Result<PatrolTaskVO>> update(@PathVariable Long id,
                                             @RequestBody PatrolTaskUpdateRequest req) {
        return taskAppService.revise(id, req.stationId(), req.siteId(), req.patrolType(),
                        req.plannedDate(), req.executor())
                .map(PatrolTaskVoConverter::toVo)
                .map(Result::ok);
    }

    /** 取消：待执行 / 执行中 -> 已取消，重复取消幂等；账留着，默认名单不再带它。 */
    @PostMapping("/{id}/cancel")
    public Mono<Result<PatrolTaskVO>> cancel(@PathVariable Long id) {
        return taskAppService.cancel(id)
                .map(PatrolTaskVoConverter::toVo)
                .map(Result::ok);
    }

    /**
     * 翻任务：stationId/siteId/patrolType/status/plannedDate 条件随意拼，全空翻整份。
     * 默认不带已取消任务；显式 status=CANCELLED 才翻得出已取消的。
     */
    @GetMapping({"", "/list"})
    public Mono<Result<PageVO<PatrolTaskVO>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String patrolType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate plannedDate) {
        return taskAppService.pageTasks(pageNum, pageSize, stationId, siteId,
                        patrolType, status, plannedDate)
                .map(PatrolTaskVoConverter::toPageVo)
                .map(Result::ok);
    }
}
