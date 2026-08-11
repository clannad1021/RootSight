package kg.edu.nagisa.rootsight.common.exception;

import lombok.experimental.StandardException;

/**
 * 模型或诊断链路暂时不可用时抛出的统一业务异常。
 */
@StandardException
public class DiagnosisUnavailableException extends RuntimeException {
}
