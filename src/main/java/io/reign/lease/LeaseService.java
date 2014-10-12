/*
 Copyright 2014 Yen Pai ypai@reign.io

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package io.reign.lease;

import io.reign.Service;

import java.util.concurrent.TimeUnit;

/**
 * 
 * @author ypai
 * 
 */
public interface LeaseService extends Service {

	public void request(String clusterId, String leaseId, int poolSize, long leaseDuration, TimeUnit leaseDurationUnit,
	        LeaseObserver leaseObserver);

	public void observe(String clusterId, String leaseId, int poolSize, long leaseDuration, TimeUnit leaseDurationUnit,
	        LeaseObserver leaseObserver);

}