package com.architect.service;

import com.architect.model.ScanQueueTask;
import com.architect.model.ScanTaskStatus;
import com.architect.repository.ScanQueueTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScanJobAsyncExecutor {

    private final ScanQueueTaskRepository scanQueueTaskRepository;
    private final RepoScannerService repoScannerService;
    private final ScanConcurrencyService scanConcurrencyService;
    private final TransactionTemplate transactionTemplate;

    @Async("scanExecutor")
    public void executeScanJob(Long taskId) {
        ScanQueueTask task = scanQueueTaskRepository.findByIdForExecution(taskId).orElse(null);
        if (task == null || task.getStatus() != ScanTaskStatus.RUNNING) {
            return;
        }
        Long userId = task.getUser().getId();
        long repoId = task.getRepo().getId();

        if (!scanConcurrencyService.tryAcquire(userId)) {
            transactionTemplate.executeWithoutResult(s -> {
                int reverted = scanQueueTaskRepository.revertToQueued(taskId);
                if (reverted > 0) {
                    log.warn("scan_requeued repo={} user={} reason=capacity", repoId, userId);
                }
            });
            return;
        }

        long t0 = System.currentTimeMillis();
        try {
            int activeUser = (int) scanQueueTaskRepository.countByUser_IdAndStatus(userId, ScanTaskStatus.RUNNING);
            int activeGlobal = (int) scanQueueTaskRepository.countByStatus(ScanTaskStatus.RUNNING);
            log.info("scan_started repo={} user={} type={} activeUser={} activeGlobal={} pickedBy={} instanceJvmGlobal={} instanceJvmUser={}",
                    repoId, userId, task.getType(), activeUser, activeGlobal, task.getPickedBy(),
                    scanConcurrencyService.globalActive(), scanConcurrencyService.userActive(userId));

            RepoScannerService.ScanMode mode = RepoScannerService.ScanMode.valueOf(task.getScanMode());
            repoScannerService.scanRepoInternal(repoId, userId, mode);

            task.setStatus(ScanTaskStatus.DONE);
            task.setLastError(null);
            scanQueueTaskRepository.save(task);
            log.info("scan_completed repo={} user={} durationMs={}", repoId, userId, System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.error("Scan task failed repo={} user={} taskId={}", repoId, userId, taskId, e);
            task.setStatus(ScanTaskStatus.FAILED);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg != null && msg.length() > 2000) {
                msg = msg.substring(0, 2000) + "...";
            }
            task.setLastError(msg);
            scanQueueTaskRepository.save(task);
        } finally {
            scanConcurrencyService.release(userId);
        }
    }
}
