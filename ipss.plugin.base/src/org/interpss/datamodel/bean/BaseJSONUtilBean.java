/*
 * @(#)BaseJSONUtilBean.java   
 *
 * Copyright (C) 2008-2020 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * @Author Mike Zhou
 * @Version 1.0
 * @Date 04/10/2020
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.datamodel.bean;

import com.google.gson.Gson;

/**
 * 
 * @author mzhou
 *
 */
public abstract class BaseJSONUtilBean {
	@Override
	public String toString() {
		return new Gson().toJson(this);
	}
}