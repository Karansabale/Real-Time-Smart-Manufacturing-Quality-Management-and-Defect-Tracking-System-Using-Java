package com.project.qms.entity;

/** The kind of defect found (FR-06.3). Seven values, fixed at design time. */
public enum DefectCategory {
    DIMENSIONAL,
    SURFACE_FINISH,
    MATERIAL,
    ASSEMBLY,
    FUNCTIONAL,
    PACKAGING,
    OTHER
}
