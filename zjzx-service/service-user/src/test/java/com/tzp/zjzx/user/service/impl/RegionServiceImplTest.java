package com.tzp.zjzx.user.service.impl;

import com.tzp.zjzx.model.entity.base.Region;
import com.tzp.zjzx.model.vo.h5.RegionVo;
import com.tzp.zjzx.user.mapper.RegionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionServiceImplTest {

    @Mock
    private RegionMapper regionMapper;

    @InjectMocks
    private RegionServiceImpl regionService;

    @Test
    void rootCodeReturnsProvinces() {
        Region province = new Region();
        province.setCode("110000");
        province.setParentCode(0L);
        province.setName("北京市");
        province.setLevel(1);
        when(regionMapper.findChildren("0")).thenReturn(List.of(province));

        List<RegionVo> result = regionService.findChildren("0");

        assertEquals(1, result.size());
        assertEquals("110000", result.get(0).getCode());
        assertTrue(result.get(0).getHasChildren());
        verify(regionMapper).findChildren("0");
    }

    @Test
    void malformedParentCodeReturnsEmptyList() {
        List<RegionVo> result = regionService.findChildren("1100x0");

        assertTrue(result.isEmpty());
        verifyNoInteractions(regionMapper);
    }
}
