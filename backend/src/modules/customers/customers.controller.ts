import { Body, Controller, Get, Param, Post, Put } from '@nestjs/common';

@Controller('admin/customers')
export class CustomersController {
  @Get()
  findAll() {
    return [
      {
        id: 'demo',
        name: 'Cliente Demo',
        status: 'TRIAL',
        expiresAt: null,
        maxDevices: 1,
      },
    ];
  }

  @Post()
  create(@Body() body: unknown) {
    return { id: 'new-customer', ...(body as object) };
  }

  @Put(':id')
  update(@Param('id') id: string, @Body() body: unknown) {
    return { id, ...(body as object) };
  }
}
